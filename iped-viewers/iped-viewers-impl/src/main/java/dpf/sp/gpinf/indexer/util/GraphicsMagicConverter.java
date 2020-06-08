/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.util;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import dpf.sp.gpinf.indexer.ConstantsViewer;

public class GraphicsMagicConverter implements Closeable {

    private static final String RESOLUTION = "resolution"; //$NON-NLS-1$
    private static final String THREADS = "threads"; //$NON-NLS-1$
    private static final String IM_TEMP_PATH = "MAGICK_TEMPORARY_PATH"; //$NON-NLS-1$
    private static final String GM_TEMP_PATH = "MAGICK_TMPDIR"; //$NON-NLS-1$
    private static final String MAGICK_MEMORY_LIMIT = "MAGICK_AREA_LIMIT"; //$NON-NLS-1$
    private static final String MAGICK_MEMORY_LIMIT_VAL = "10MP"; //$NON-NLS-1$

    private static String[] CMD = { "gm", "convert", "-limit", THREADS, "1", "-density", "96", "-sample", RESOLUTION, "-", "bmp:-" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
    private static final String tmpDirName = "gm-im_temp"; //$NON-NLS-1$
    private static final String winToolPath = "/tools/imagemagick"; //$NON-NLS-1$

    public static boolean USE_GM = false;
    public static int TIMEOUT = 10;
    public static boolean enabled = true;
    private static String toolPath = ""; //$NON-NLS-1$

    private static File tmpDir;

    private static boolean imageMagickConfigured = false;

    private ExecutorService executorService = null;
    private boolean ownsExecutor;

    static {
        try {
            if (!System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                toolPath = ""; //$NON-NLS-1$
            } else
                toolPath = "indexador" + winToolPath; //$NON-NLS-1$

            tmpDir = new File(ConstantsViewer.indexerTemp, tmpDirName);
            tmpDir.mkdirs();
            startTmpDirCleaner();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public GraphicsMagicConverter(ExecutorService executorService) {
        super();
        this.executorService = executorService;
        this.ownsExecutor = false;
    }

    public GraphicsMagicConverter() {
        super();
        this.executorService = Executors.newCachedThreadPool();
        this.ownsExecutor = true;
    }

    public static void setWinToolPathPrefix(String prefix) {
        toolPath = prefix + winToolPath;
    }

    private static void startTmpDirCleaner() {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    File[] subFiles = tmpDir.listFiles();
                    if (subFiles != null) {
                        for (File tmp : subFiles) {
                            tmp.delete();
                        }
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void configureImageMagick() {
        if (imageMagickConfigured)
            return;
        String[] cmd = { "magick", "-version" };
        if (!toolPath.isEmpty())
            cmd[0] = toolPath + "/" + cmd[0];
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            while (p.getInputStream().read(new byte[1024]) != -1)
                ;
            int exit = p.waitFor();
            if (exit == 0) {
                CMD[0] = "magick";
                for (int i = 1; i < CMD.length; i++) {
                    if (CMD[i].equals(THREADS)) {
                        CMD[i] = "thread";
                    }
                }
            } else
                throw new IOException("error");

        } catch (IOException e) {
            // can not run program
            String[] newCmd = new String[CMD.length - 1];
            System.arraycopy(CMD, 1, newCmd, 0, newCmd.length);
            CMD = newCmd;

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        imageMagickConfigured = true;
    }

    private String[] getCmd(int resolution) {

        if (!USE_GM && !imageMagickConfigured) {
            configureImageMagick();
        }
        String[] cmd = new String[CMD.length];
        for (int i = 0; i < cmd.length; i++) {
            cmd[i] = CMD[i];
            if (!toolPath.isEmpty() && i == 0) {
                cmd[0] = toolPath + "/" + cmd[0]; //$NON-NLS-1$
            }
            if (cmd[i].equals(RESOLUTION)) {
                cmd[i] = String.valueOf(resolution);
            }
        }
        return cmd;
    }

    public BufferedImage getImage(final InputStream in, final int resolution) {
        try {
            return getImage(in, resolution, false);

        } catch (TimeoutException e) {
            return null;
        }
    }

    public BufferedImage getImage(InputStream in, int resolution, boolean throwTimeout) throws TimeoutException {

        if (!enabled) {
            return null;
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put(GM_TEMP_PATH, tmpDir.getAbsolutePath());
        pb.environment().put(IM_TEMP_PATH, tmpDir.getAbsolutePath());
        pb.environment().put(MAGICK_MEMORY_LIMIT, MAGICK_MEMORY_LIMIT_VAL);

        pb.command(getCmd(resolution));
        Process p = null;
        try {
            p = pb.start();
        } catch (IOException e1) {
            Log.error("ImageMagickConverter", "Error executing imageMagick/graphicsMagick. " //$NON-NLS-1$ //$NON-NLS-2$
                    + "Check if it is installed and if its path is configured!"); //$NON-NLS-1$
        }
        BufferedImage result = null;
        if (p != null) {
            Future<?> sendFuture = sendInputStream(in, p);
            ignoreErrorStream(p);
            Future<BufferedImage> resultFuture = getResultFuture(p);
            try {
                sendFuture.get(TIMEOUT, TimeUnit.SECONDS);
                result = resultFuture.get(TIMEOUT, TimeUnit.SECONDS);

            } catch (TimeoutException | InterruptedException e) {
                if (throwTimeout) {
                    if (e instanceof TimeoutException) {
                        throw (TimeoutException) e;
                    } else {
                        TimeoutException te = new TimeoutException(e.getMessage());
                        te.initCause(e);
                        throw te;
                    }
                } else {
                    Log.warning("ImageMagickConverter", "Timeout while converting image to BMP."); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } catch (ExecutionException e) {
                throw new RuntimeException(e);

            } finally {
                try {
                    p.destroy();
                    boolean exited = p.waitFor(2, TimeUnit.SECONDS);
                    if (!exited) {
                        p.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return result;
    }

    private Future<BufferedImage> getResultFuture(final Process p) {
        Callable<BufferedImage> callable = new ResultCallable(p);
        return submit(callable);
    }

    private void ignoreErrorStream(final Process p) {
        Runnable runnable = new IgnoreErroStreamRunnable(p);
        submit(runnable);
    }

    private Future<?> sendInputStream(final InputStream in, final Process p) {
        Runnable runnable = new SendInputStreamRunnable(p, in);
        return submit(runnable);
    }

    private <V> Future<V> submit(Callable<V> callable) {
        return executorService.submit(callable);
    }

    private Future<?> submit(Runnable runnable) {
        return executorService.submit(runnable);
    }

    public void finish(long timeoutInMilis) {
        if (ownsExecutor && executorService != null) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(timeoutInMilis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        finish(10000);
    }

    private static class IgnoreErroStreamRunnable implements Runnable {
        private final Process p;

        private IgnoreErroStreamRunnable(Process p) {
            this.p = p;
        }

        @Override
        public void run() {
            InputStream in = p.getErrorStream();
            int i = 0;
            byte[] buf = new byte[8192];
            try {
                while (i != -1 && p.isAlive()) {
                    i = in.read(buf);
                }

            } catch (IOException e) {
            } finally {
                IOUtil.closeQuietly(in);
            }
        }
    }

    private class ResultCallable implements Callable<BufferedImage> {
        private final Process p;

        private ResultCallable(Process p) {
            this.p = p;
        }

        @Override
        public BufferedImage call() {
            InputStream in = p.getInputStream();
            try {
                return ImageIO.read(in);
            } catch (IOException e) {
                return null;
            } finally {
                IOUtil.closeQuietly(in);
            }
        }
    }

    private static class SendInputStreamRunnable implements Runnable {
        private final Process p;
        private final InputStream in;

        private SendInputStreamRunnable(Process p, InputStream in) {
            this.p = p;
            this.in = in;
        }

        @Override
        public void run() {
            OutputStream out = p.getOutputStream();
            int i = 0;
            byte[] buf = new byte[64 * 1024];
            try {
                while (i != -1 && p.isAlive()) {
                    out.write(buf, 0, i);
                    out.flush();
                    i = in.read(buf);
                }
            } catch (IOException e) {
                // e.printStackTrace();
            } finally {
                IOUtil.closeQuietly(out);
            }
        }
    }

}
