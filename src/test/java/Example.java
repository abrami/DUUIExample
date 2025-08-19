import org.apache.commons.compress.compressors.CompressorException;
import org.apache.uima.UIMAException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.dkpro.core.io.xmi.XmiWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.texttechnology.duui.reader.BundestagReader;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIDockerDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIUIMADriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUIAsynchronousProcessor;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUICollectionReader;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.reader.DUUIFileReaderLazy;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

public class Example {

    private static DUUIComposer pComposer = null;
    private static int iWorkers = 1;

    @BeforeAll
    public static void init() throws IOException, URISyntaxException, UIMAException, SAXException {

        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();

        pComposer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(ctx)
                .withWorkers(iWorkers);

        DUUIUIMADriver uima_driver = new DUUIUIMADriver();
        DUUIRemoteDriver remoteDriver = new DUUIRemoteDriver();
        DUUIDockerDriver dockerDriver = new DUUIDockerDriver();

        pComposer.addDriver(uima_driver, remoteDriver, dockerDriver);

    }

    @Test
    @DisplayName("Jsoup")
    public void read() throws Exception {

        DUUICollectionReader pReader = new BundestagReader("Bundestag", "1058442-1058442");

        DUUIAsynchronousProcessor pProcessor = new  DUUIAsynchronousProcessor(pReader);

//        pComposer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/textimager-duui-spacy-single-de_core_news_sm:0.1.4")
//                .withImageFetching()
//                .withScale(iWorkers)
//                .build());
//
//        pComposer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/gervader_duui:1.0.2")
//                .withParameter("selection", "text") // nessecary
//                .withImageFetching()
//                .withScale(iWorkers)
//                .build());

        pComposer.add(new DUUIUIMADriver.Component(createEngineDescription(XmiWriter.class,
                XmiWriter.PARAM_TARGET_LOCATION, "/tmp/new/",
                XmiWriter.PARAM_PRETTY_PRINT, true,
                XmiWriter.PARAM_OVERWRITE, true,
                XmiWriter.PARAM_VERSION, "1.1",
                XmiWriter.PARAM_COMPRESSION, "GZIP"
        )).build());

        pComposer.run(pProcessor, "bundestag");

    }

}
