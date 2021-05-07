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
package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;

import br.gov.pf.labld.graph.GraphTask;
import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Messages;
import dpf.sp.gpinf.indexer.WorkerProvider;
import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.datasource.FTK3ReportReader;
import dpf.sp.gpinf.indexer.datasource.ItemProducer;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.process.task.ExportCSVTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.IndexTask;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.IndexerSimilarity;
import dpf.sp.gpinf.indexer.util.ConfiguredFSDirectory;
import dpf.sp.gpinf.indexer.util.CustomIndexDeletionPolicy;
import dpf.sp.gpinf.indexer.util.ExeFileFilter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.SleuthkitClient;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.Item;
import iped3.ICaseData;
import iped3.IItem;
import iped3.search.IItemSearcher;
import iped3.search.LuceneSearchResult;
import iped3.util.BasicProps;

/**
 * Classe responsável pela preparação do processamento, inicialização do
 * contador, produtor e consumidores (workers) dos itens, monitoramento do
 * processamento e pelas etapas pós-processamento.
 *
 * O contador apenas enumera e soma o tamanho dos itens que serão processados,
 * permitindo que seja estimado o progresso e término do processamento.
 *
 * O produtor obtém os itens a partir de uma fonte de dados específica
 * (relatório do FTK, diretório, imagem), inserindo-os numa fila de
 * processamento com tamanho limitado (para limitar o uso de memória).
 *
 * Os consumidores (workers) retiram os itens da fila e são responsáveis pelo
 * seu processamento. Cada worker executa em uma thread diferente, permitindo o
 * processamento em paralelo dos itens. Por padrão, o número de workers é igual
 * ao número de processadores disponíveis.
 *
 * Após inicializar o processamento, o manager realiza o monitoramento,
 * verificando se alguma exceção ocorreu, informando a interface sobre o estado
 * do processamento e verificando se os workers processaram todos os itens.
 *
 * O pós-processamento inclui a pré-ordenação das propriedades dos itens, o
 * armazenamento do volume de texto indexado de cada item, do mapeamento indexId
 * para id, dos ids dos itens fragmentados, a filtragem de categorias e
 * palavras-chave e o log de estatísticas do processamento.
 *
 */
public class Manager {

    private static long commitIntervalMillis = 30 * 60 * 1000;
    private static int QUEUE_SIZE = 100000;
    private static Logger LOGGER = LogManager.getLogger(Manager.class);
    private static String FINISHED_FLAG = "data/processing_finished";
    private static Manager instance;

    private ICaseData caseData;

    private List<File> sources;
    private File output, finalIndexDir, indexDir, palavrasChave;

    private ItemProducer counter, producer;
    private Worker[] workers;
    private IndexWriter writer;

    public Statistics stats;
    public Exception exception;

    private boolean isSearchAppOpen = false;
    private boolean isProcessingFinished = false;

    private LocalConfig localConfig;
    private AdvancedIPEDConfig advancedConfig;
    private CmdLineArgs args;

    private Thread commitThread = null;
    AtomicLong partialCommitsTime = new AtomicLong();

    private final AtomicBoolean initSleuthkitServers = new AtomicBoolean(false);
    
    public static Manager getInstance() {
        return instance;
    }

    public ICaseData getCaseData() {
        return caseData;
    }

    public Manager(List<File> sources, File output, File palavras) {

        this.localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class).iterator()
                .next();
        this.advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();

        this.indexDir = localConfig.getIndexTemp();
        this.sources = sources;
        this.output = output;
        this.palavrasChave = palavras;

        this.caseData = new CaseData(QUEUE_SIZE);

        Item.setStartID(0);

        finalIndexDir = new File(output, "index"); //$NON-NLS-1$

        if (indexDir == null) {
            indexDir = finalIndexDir;
        }

        stats = Statistics.get(caseData, finalIndexDir);

        instance = this;

        commitIntervalMillis = advancedConfig.getCommitIntervalSeconds() * 1000;
    }

    public File getIndexTemp() {
        return indexDir;
    }

    Worker[] getWorkers() {
        return workers;
    }

    public IndexWriter getIndexWriter() {
        return this.writer;
    }

    public void process() throws Exception {

        stats.printSystemInfo();

        Files.deleteIfExists(getFinishedFileFlag(output).toPath());

        output = output.getCanonicalFile();

        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());

        prepareOutputFolder();

        if ((args.isContinue() || args.isRestart())) {
            if (finalIndexDir.exists()) {
                indexDir = finalIndexDir;
            } else if (indexDir != finalIndexDir) {
                changeTempDir();
            }
        }

        if (args.getEvidenceToRemove() != null) {
            indexDir = finalIndexDir;
        }

        saveCurrentTempDir();

        int i = 1;
        for (File source : sources) {
            LOGGER.info("Evidence " + (i++) + ": '{}'", source.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        try {
            if (!iniciarIndexacao())
                return;

            // apenas conta o número de arquivos a indexar
            counter = new ItemProducer(this, caseData, true, sources, output);
            counter.start();

            // produz lista de arquivos e propriedades a indexar
            producer = new ItemProducer(this, caseData, false, sources, output);
            producer.start();

            monitorarIndexacao();

            finalizarIndexacao();

        } catch (Exception e) {
            interromperIndexacao();
            throw e;

        } finally {
            closeItemProducers();
        }

        filtrarPalavrasChave();

        removeEmptyTreeNodes();

        new P2PBookmarker(caseData).createBookmarksForSharedFiles(output.getParentFile());

        updateImagePaths();

        shutDownSleuthkitServers();

        deleteTempDir();

        stats.logarEstatisticas(this);

        Files.createFile(getFinishedFileFlag(output).toPath());

    }

    private static File getFinishedFileFlag(File output) {
        return new File(output, FINISHED_FLAG);
    }

    public static boolean isProcessingFinishedOK(File moduleDir) {
        return getFinishedFileFlag(moduleDir).exists();
    }

    private void closeItemProducers() {
        if (counter != null) {
            try {
                counter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (producer != null) {
            try {
                producer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void interromperIndexacao() throws Exception {
        if (workers != null) {
            for (int k = 0; k < workers.length; k++) {
                if (workers[k] != null) {
                    workers[k].interrupt();
                    // workers[k].join(5000);
                }
            }
        }
        ParsingReader.shutdownTasks();
        if (writer != null) {
            writer.rollback();
        }

        if (counter != null) {
            counter.interrupt();
            // contador.join(5000);
        }
        if (producer != null) {
            producer.interrupt();
            // produtor.join(5000);
        }
    }

    public void initSleuthkitServers(final String dbPath) throws InterruptedException {
        if (!initSleuthkitServers.getAndSet(true)) {
            SleuthkitClient.initSleuthkitServers(dbPath);
        }
    }

    private void shutDownSleuthkitServers() {
        LOGGER.info("Closing Sleuthkit Servers."); //$NON-NLS-1$
        SleuthkitClient.shutDownServers();
    }

    private void saveCurrentTempDir() throws UnsupportedEncodingException, IOException {
        File temp = localConfig.getIndexerTemp();
        File prevTempInfoFile = IPEDSource.getTempDirInfoFile(output);
        prevTempInfoFile.getParentFile().mkdirs();
        Files.write(prevTempInfoFile.toPath(), temp.getAbsolutePath().getBytes("UTF-8"));
    }

    private void changeTempDir() throws UnsupportedEncodingException, IOException {
        File prevIndexTemp = IPEDSource.getTempIndexDir(output);
        if (!prevIndexTemp.exists()) {
            return;
        }
        localConfig.setIndexerTemp(prevIndexTemp.getParentFile());
        indexDir = localConfig.getIndexTemp();
    }

    private void loadExistingData() throws IOException {

        try (IndexReader reader = DirectoryReader.open(writer, true, true)) {
            stats.previousIndexedFiles = reader.numDocs();
        }

        if (new File(output, "data/containsReport.flag").exists()) { //$NON-NLS-1$
            caseData.setContainsReport(true);
        }

    }

    private IndexWriterConfig getIndexWriterConfig() {
        IndexWriterConfig conf = new IndexWriterConfig(AppAnalyzer.get());
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        conf.setCommitOnClose(true);
        conf.setSimilarity(new IndexerSimilarity());
        ConcurrentMergeScheduler mergeScheduler = new ConcurrentMergeScheduler();
        mergeScheduler.disableAutoIOThrottle();
        if ((localConfig.isIndexTempOnSSD() && indexDir != finalIndexDir) || localConfig.isOutputOnSSD()) {
            mergeScheduler.setMaxMergesAndThreads(8, 4);
        }
        conf.setMergeScheduler(mergeScheduler);
        conf.setRAMBufferSizeMB(64);
        TieredMergePolicy tieredPolicy = new TieredMergePolicy();
        /*
         * Seta tamanho máximo dos subíndices. Padrão é 5GB. Poucos subíndices grandes
         * impactam processamento devido a merges parciais demorados. Muitos subíndices
         * pequenos aumentam tempo e memória necessários p/ pesquisas. Usa 4000MB devido
         * a limite do ISO9660
         */
        tieredPolicy.setMaxMergedSegmentMB(4000);
        conf.setMergePolicy(tieredPolicy);

        conf.setIndexDeletionPolicy(new CustomIndexDeletionPolicy(args));

        return conf;
    }

    private void removeEvidence(String uuid) throws IOException {
        Level CONSOLE = Level.getLevel("MSG"); //$NON-NLS-1$
        LOGGER.log(CONSOLE,
                "WARN: removing evidence does NOT update duplicate flag, graph and internal storage for now!");
        LOGGER.log(CONSOLE, "Removing evidence with UUID {} from index...", uuid);
        TermQuery query = new TermQuery(new Term(BasicProps.EVIDENCE_UUID, uuid));
        int prevDocs = writer.numDocs();
        writer.deleteDocuments(query);
        writer.commit();
        int deletes = prevDocs - writer.numDocs();
        LOGGER.log(CONSOLE, "Deleted about {} raw documents from index.", deletes);
        writer.close();
    }

    private boolean iniciarIndexacao() throws Exception {
        WorkerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CreatingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        LOGGER.info("Creating index..."); //$NON-NLS-1$

        boolean newIndex = !indexDir.exists();
        Directory directory = ConfiguredFSDirectory.open(indexDir);
        IndexWriterConfig config = getIndexWriterConfig();
        
        if (args.isRestart()) {
            List<IndexCommit> commits = DirectoryReader.listCommits(directory);
            config.setIndexCommit(commits.get(0));
        }

        writer = new IndexWriter(directory, config);
        if (newIndex) {
            // first empty commit to be used by --restart
            writer.commit();
        }

        if (args.isAppendIndex() || args.isContinue() || args.isRestart()) {
            loadExistingData();
        }

        if (args.getEvidenceToRemove() != null) {
            removeEvidence(args.getEvidenceToRemove());
            return false;
        }

        workers = new Worker[localConfig.getNumThreads()];
        for (int k = 0; k < workers.length; k++) {
            workers[k] = new Worker(k, caseData, writer, output, this);
        }

        // Execução dos workers após todos terem sido instanciados e terem inicializado
        // suas tarefas
        for (int k = 0; k < workers.length; k++) {
            workers[k].start();
        }

        WorkerProvider.getInstance().firePropertyChange("workers", 0, workers); //$NON-NLS-1$

        return true;
    }

    private void monitorarIndexacao() throws Exception {

        boolean someWorkerAlive = true;
        long start = System.currentTimeMillis();

        while (someWorkerAlive) {
            if (WorkerProvider.getInstance().isCancelled()) {
                exception = new IPEDException("Processing canceled!"); //$NON-NLS-1$
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                exception = new IPEDException("Processing canceled!"); //$NON-NLS-1$
            }

            String currentDir = counter.currentDirectory();
            if (counter.isAlive() && currentDir != null && !currentDir.trim().isEmpty()) {
                WorkerProvider.getInstance().firePropertyChange("mensagem", 0, //$NON-NLS-1$
                        Messages.getString("Manager.Adding") + currentDir.trim() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
            }
            WorkerProvider.getInstance().firePropertyChange("discovered", 0, caseData.getDiscoveredEvidences()); //$NON-NLS-1$
            WorkerProvider.getInstance().firePropertyChange("processed", -1, stats.getProcessed()); //$NON-NLS-1$
            WorkerProvider.getInstance().firePropertyChange("progresso", 0, (int) (stats.getVolume() / 1000000)); //$NON-NLS-1$

            someWorkerAlive = false;
            for (int k = 0; k < workers.length; k++) {
                if (workers[k].exception != null && exception == null) {
                    exception = workers[k].exception;
                }
                /**
                 * TODO sincronizar teste, pois pode ocorrer condição de corrida e o teste não
                 * detectar um último item sendo processado não é demasiado grave pois será
                 * detectado o problema no log de estatísticas e o usuario sera informado do
                 * erro.
                 */
                if (workers[k].evidence != null || workers[k].itensBeingProcessed > 0)
                    someWorkerAlive = true;
            }
            
            IItem queueEnd = caseData.getItemQueue().peek();
            boolean justQueueEndLeft = queueEnd != null && queueEnd.isQueueEnd() && caseData.getItemQueue().size() == 1;

            if (!justQueueEndLeft || producer.isAlive())
                someWorkerAlive = true;

            if (!someWorkerAlive) {
                IItemSearcher searcher = (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
                if (searcher != null)
                    searcher.close();

                if (caseData.changeToNextQueue() != null) {
                    LOGGER.info("Changed to processing queue with priority " + caseData.getCurrentQueuePriority()); //$NON-NLS-1$

                    caseData.putCaseObject(IItemSearcher.class.getName(),
                            new ItemSearcher(output.getParentFile(), writer));
                    caseData.getItemQueue().addLast(queueEnd);
                    someWorkerAlive = true;
                    for (int k = 0; k < workers.length; k++)
                        workers[k].processNextQueue();
                }
            }

            long t = System.currentTimeMillis();
            if (t - start >= commitIntervalMillis) {
                if (commitThread == null || !commitThread.isAlive()) {
                    commitThread = commit();
                }
                start = t;
            }

            if (exception != null) {
                throw exception;
            }

        }

    }

    private Thread commit() {
        // commit could be costly, do in another thread
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    long start = System.currentTimeMillis() / 1000;
                    LOGGER.info("Prepare commit started...");
                    writer.prepareCommit();

                    // commit other control data
                    IndexTask.saveExtraAttributes(output);
                    IndexItem.saveMetadataTypes(new File(output, "conf")); //$NON-NLS-1$
                    stats.commit();

                    LOGGER.info("Commiting sqlite storages...");
                    ExportFileTask.commitStorage(output);

                    GraphTask.commit();

                    ExportCSVTask.commit(output);

                    writer.commit();
                    long end = System.currentTimeMillis() / 1000;
                    LOGGER.info("Commit finished in " + (end - start) + "s");
                    partialCommitsTime.addAndGet(end - start);

                } catch (Exception e) {
                    exception = e;
                    try {
                        LOGGER.error("Error commiting. Rollback commit started...");
                        writer.rollback();
                        LOGGER.error("Rollback commit finished.");

                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        };
        t.start();
        return t;
    }

    public synchronized int numItensBeingProcessed() {
        int num = 0;
        for (int k = 0; k < workers.length; k++) {
            num += workers[k].itensBeingProcessed;
        }
        return num;
    }

    private void finalizarIndexacao() throws Exception {

        if (commitThread != null && commitThread.isAlive()) {
            commitThread.join();
            if (exception != null) {
                throw exception;
            }
        }

        for (int k = 0; k < workers.length; k++) {
            workers[k].finish();
        }

        if (advancedConfig.isForceMerge()) {
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.Optimizing")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("Optimizing Index..."); //$NON-NLS-1$
            try {
                writer.forceMerge(1);
            } catch (Throwable e) {
                LOGGER.error("Error while optimizing: {}", e); //$NON-NLS-1$
            }

        }

        stats.commit();

        WorkerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.ClosingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        LOGGER.info("Closing Index..."); //$NON-NLS-1$
        writer.close();
        writer = null;

        if (!indexDir.getCanonicalPath().equalsIgnoreCase(finalIndexDir.getCanonicalPath())) {
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", Messages.getString("Manager.CopyingIndex")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            LOGGER.info("Moving Index..."); //$NON-NLS-1$
            try {
                Files.move(indexDir.toPath(), finalIndexDir.toPath());

            } catch (IOException e) {
                LOGGER.info("Move failed. Copying Index..."); //$NON-NLS-1$
                IOUtil.copiaDiretorio(indexDir, finalIndexDir);
            }
        }

        if (caseData.containsReport()) {
            new File(output, "data/containsReport.flag").createNewFile(); //$NON-NLS-1$
        }

        if (FTK3ReportReader.wasExecuted) {
            new File(output, "data/containsFTKReport.flag").createNewFile(); //$NON-NLS-1$
        }

    }

    private void updateImagePaths() {
        if (args.isPortable()) { // $NON-NLS-1$
            IPEDSource ipedCase = new IPEDSource(output.getParentFile());
            ipedCase.updateImagePathsToRelative();
            ipedCase.close();
        }
    }

    public void deleteTempDir() {
        LOGGER.info("Deleting temp folder {}", localConfig.getIndexerTemp()); //$NON-NLS-1$
        IOUtil.deletarDiretorio(localConfig.getIndexerTemp());
    }

    private void filtrarPalavrasChave() {

        try {
            LOGGER.info("Filtering keywords..."); //$NON-NLS-1$
            WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                    Messages.getString("Manager.FilteringKeywords")); //$NON-NLS-1$
            ArrayList<String> palavras = Util.loadKeywords(output.getAbsolutePath() + "/palavras-chave.txt", //$NON-NLS-1$
                    Charset.defaultCharset().name());

            if (palavras.size() != 0) {
                IPEDSource ipedCase = new IPEDSource(output.getParentFile());
                ArrayList<String> palavrasFinais = new ArrayList<String>();
                for (String palavra : palavras) {
                    if (Thread.interrupted()) {
                        ipedCase.close();
                        throw new InterruptedException("Processing canceled!"); //$NON-NLS-1$
                    }

                    try {
                        IPEDSearcher pesquisa = new IPEDSearcher(ipedCase, palavra);
                        if (pesquisa.searchAll().getLength() > 0) {
                            palavrasFinais.add(palavra);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Erro filtering by {} {}", palavra, e.toString());
                    }

                }
                ipedCase.close();

                Util.saveKeywords(palavrasFinais, output.getAbsolutePath() + "/palavras-chave.txt", "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
                int filtradas = palavras.size() - palavrasFinais.size();
                LOGGER.info("Filtered {} keywords.", filtradas); //$NON-NLS-1$
            } else {
                LOGGER.info("No keywords to filter out."); //$NON-NLS-1$
            }

        } catch (Exception e) {
            LOGGER.error("Error filtering keywords", e); //$NON-NLS-1$
        }

    }

    private void removeEmptyTreeNodes() {

        if (!caseData.containsReport() || caseData.isIpedReport()) {
            return;
        }

        WorkerProvider.getInstance().firePropertyChange("mensagem", "", //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("Manager.DeletingTreeNodes")); //$NON-NLS-1$
        LOGGER.info("Deleting empty tree nodes"); //$NON-NLS-1$

        try (IPEDSource ipedCase = new IPEDSource(output.getParentFile())) {
            IPEDSearcher searchAll = new IPEDSearcher(ipedCase, new MatchAllDocsQuery());
            LuceneSearchResult result = searchAll.searchAll();

            boolean[] doNotDelete = new boolean[stats.getLastId() + 1];
            for (int docID : result.getLuceneIds()) {
                String parentIds = ipedCase.getReader().document(docID).get(IndexItem.PARENTIDs);
                if (!parentIds.trim().isEmpty()) {
                    for (String parentId : parentIds.trim().split(" ")) { //$NON-NLS-1$
                        doNotDelete[Integer.parseInt(parentId)] = true;
                    }
                }
            }

            writer = new IndexWriter(ConfiguredFSDirectory.open(finalIndexDir), getIndexWriterConfig());

            int startId = 0, interval = 1000, endId = interval;
            while (startId <= stats.getLastId()) {
                if (endId > stats.getLastId()) {
                    endId = stats.getLastId();
                }
                BooleanQuery.Builder builder = new BooleanQuery.Builder()
                        .add(new TermQuery(new Term(IndexItem.TREENODE, "true")), Occur.MUST) //$NON-NLS-1$
                        .add(IntPoint.newRangeQuery(IndexItem.ID, startId, endId), Occur.MUST);
                for (int i = startId; i <= endId; i++) {
                    if (doNotDelete[i]) {
                        builder.add(IntPoint.newExactQuery(IndexItem.ID, i), Occur.MUST_NOT);
                    }
                }
                BooleanQuery query = builder.build();
                writer.deleteDocuments(query);
                startId = endId + 1;
                endId += interval;
            }

        } catch (Exception e) {
            LOGGER.warn("Error deleting empty tree nodes", e); //$NON-NLS-1$

        } finally {
            IOUtil.closeQuietly(writer);
        }

    }

    private void prepareOutputFolder() throws Exception {
        if (output.exists() && !args.isAppendIndex() && !args.isContinue() && !args.isRestart()
                && args.getEvidenceToRemove() == null) {
            throw new IPEDException("Directory already exists: " + output.getAbsolutePath()); //$NON-NLS-1$
        }

        File export = new File(output.getParentFile(), ExportFileTask.EXTRACT_DIR);
        if (export.exists() && !args.isAppendIndex() && !args.isContinue() && !args.isRestart()
                && args.getEvidenceToRemove() == null) {
            throw new IPEDException("Directory already exists: " + export.getAbsolutePath()); //$NON-NLS-1$
        }

        if (!output.exists() && !output.mkdirs()) {
            throw new IOException("Fail to create folder " + output.getAbsolutePath()); //$NON-NLS-1$
        }

        if (!args.isAppendIndex() && !args.isContinue() && !args.isRestart() && args.getEvidenceToRemove() == null) {
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "lib"), new File(output, "lib"), true); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "jre"), new File(output, "jre"), true); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "tools"), new File(output, "tools")); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, iped3.util.Messages.BUNDLES_FOLDER),
                    new File(output, iped3.util.Messages.BUNDLES_FOLDER), true); // $NON-NLS-1$ //$NON-NLS-2$

            if (!advancedConfig.isEmbutirLibreOffice()) {
                new File(output, "tools/libreoffice.zip").delete(); //$NON-NLS-1$
            }

            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "htm"), new File(output, "htm")); //$NON-NLS-1$ //$NON-NLS-2$
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "htmlreport"), //$NON-NLS-1$
                    new File(output, "htmlreport")); //$NON-NLS-1$
            // copy default conf folder
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().appRoot, "conf"), new File(output, "conf"));
            IOUtil.copiaDiretorio(new File(Configuration.getInstance().configPath, "conf"), new File(output, "conf"), //$NON-NLS-1$ //$NON-NLS-2$
                    true);
            IOUtil.copiaArquivo(new File(Configuration.getInstance().configPath, Configuration.CONFIG_FILE),
                    new File(output, Configuration.CONFIG_FILE));
            IOUtil.copiaArquivo(new File(Configuration.getInstance().appRoot, Configuration.LOCAL_CONFIG),
                    new File(output, Configuration.LOCAL_CONFIG));
            File binDir = new File(Configuration.getInstance().appRoot, "bin"); //$NON-NLS-1$
            if (binDir.exists())
                IOUtil.copiaDiretorio(binDir, output.getParentFile()); // $NON-NLS-1$
            else {
                for (File f : new File(Configuration.getInstance().appRoot).getParentFile()
                        .listFiles(new ExeFileFilter()))
                    IOUtil.copiaArquivo(f, new File(output.getParentFile(), f.getName()));
            }
        }

        if (palavrasChave != null) {
            IOUtil.copiaArquivo(palavrasChave, new File(output, "palavras-chave.txt")); //$NON-NLS-1$
        }

        File dataDir = new File(output, "data"); //$NON-NLS-1$
        if (!dataDir.exists()) {
            if (!dataDir.mkdir()) {
                throw new IOException("Fail to create folder " + dataDir.getAbsolutePath()); //$NON-NLS-1$
            }
        }

    }

    public boolean isSearchAppOpen() {
        return isSearchAppOpen;
    }

    public void setSearchAppOpen(boolean isSearchAppOpen) {
        this.isSearchAppOpen = isSearchAppOpen;
    }

    public boolean isProcessingFinished() {
        return isProcessingFinished;
    }

    public void setProcessingFinished(boolean isProcessingFinished) {
        this.isProcessingFinished = isProcessingFinished;
    }

}
