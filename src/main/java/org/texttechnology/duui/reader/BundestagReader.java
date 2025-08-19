package org.texttechnology.duui.reader;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUICollectionReader;
import org.texttechnologylab.DockerUnifiedUIMAInterface.monitoring.AdvancedProgressMeter;
import org.texttechnologylab.annotation.AnnotationComment;
import org.texttechnologylab.annotation.DocumentAnnotation;
import org.texttechnologylab.annotation.parliament.Speech;
import org.texttechnologylab.utilities.helper.FileUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BundestagReader implements DUUICollectionReader {

    private String sBaseParam = "https://www.bundestag.de/ajax/filterlist/de/services/opendata/[ID]?limit=10&noFilterSet=true";

    private String sPeriode = "";
    private String sTitle = "";

    private AdvancedProgressMeter progress = null;

    private AtomicInteger iOffset = new AtomicInteger(0);

    private AtomicInteger iSize = new AtomicInteger(0);
    private AtomicInteger iFinish = new AtomicInteger(0);

    private int debugCount = 1;
    private int iOffsetCount = 10;

    private boolean bFinishDownload = false;

    ConcurrentLinkedQueue<Element> pElements = new ConcurrentLinkedQueue<>();

    public BundestagReader(String sTitle, String sPeriode) throws IOException {

        this.sPeriode = sPeriode;
        this.sTitle = sTitle;
        progress = new AdvancedProgressMeter(iSize.get());

        next();

    }

    private void next() throws IOException {

        if(!bFinishDownload) {
            Document pDocument = Jsoup.connect(sBaseParam.replace("[ID]", sPeriode) + "&offset=" + iOffset.get()).get();
            Elements pSelect = pDocument.select(".bt-documents-description");
            pSelect.forEach(element -> {
                pElements.add(element);
            });
            iSize.addAndGet(pSelect.size());
            progress.setMax(iSize.get());
            if (pSelect.size() < iOffsetCount) {
                bFinishDownload=true;
            } else {
                iOffset.addAndGet(iOffsetCount);
            }
        }
    }

    @Override
    public AdvancedProgressMeter getProgress() {
        return progress;
    }

    @Override
    public void getNextCas(JCas pCas) {

        Element pElement = pElements.poll();

        String sTitle = pElement.select("strong").text();

        DocumentAnnotation pAnnotation = new DocumentAnnotation(pCas);
        pAnnotation.setSubtitle(sTitle);
        pAnnotation.addToIndexes();

        String sURL = pElement.select("ul li a").attr("href");

        DocumentMetaData dmd = new DocumentMetaData(pCas);
        dmd.setDocumentId(sURL.substring(sURL.lastIndexOf("/")+1));
        dmd.setDocumentUri("/opt/corpus/"+this.sTitle+"/"+sPeriode+"/"+dmd.getDocumentId());
        dmd.setDocumentBaseUri("/opt/corpus/");
        dmd.addToIndexes();
        print(sTitle);
        AnnotationComment annoURL = new AnnotationComment(pCas);
        annoURL.setKey("URL");
        annoURL.setValue(sURL);
        annoURL.addToIndexes();

        try {
            File pFile = FileUtils.downloadFile(sURL);
            pFile.deleteOnExit();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/namespaces", false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setValidating(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document pDocument = db.parse(pFile);

            NodeList pReden = pDocument.getElementsByTagName("rede");

            StringBuilder sb = new StringBuilder();

            for(int i=0; i<pReden.getLength(); i++) {
                Node pNode = pReden.item(i);
                int iCurrLength = sb.length();
                NodeList pTexts = pNode.getChildNodes();

                for(int j=0; j<pTexts.getLength(); j++) {
                    Node pText = pTexts.item(j).getNextSibling();
                    if(pText!=null) {
                        String sNodeName = pText.getNodeName();

                        if (sNodeName.equalsIgnoreCase("name")) {
                            break;
                        } else if (sNodeName.equalsIgnoreCase("p")) {
                            if (!pText.getAttributes().getNamedItem("klasse").getTextContent().equals("redner")) {
                                if (sb.length() > 0) {
                                    sb.append(" ");
                                }
                                sb.append(pText.getTextContent());
                            }
                        }
                    }

                }

                Speech pSpeech = new Speech(pCas);
                pSpeech.setBegin(iCurrLength==0 ? 0 : iCurrLength+1);
                pSpeech.setEnd(sb.length());
                pSpeech.addToIndexes();

            }
            pCas.setDocumentText(sb.toString());
            pCas.setDocumentLanguage("de");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }

        JCasUtil.select(pCas, Speech.class).stream().forEach(r->{
            System.out.println(r);
            System.out.println(r.getCoveredText());
        });

        iFinish.incrementAndGet();
        progress.setDone(iFinish.get());
        progress.setLeft(iSize.get()-iFinish.get());
    }

    private void print(String sName){
        if (progress.getCount() > debugCount) {
            if (progress.getCount() % debugCount == 0 && progress.getCount() > 0) {
                System.out.printf("%s \t %s\n", progress, sName);
            }
        }
    }

    @Override
    public boolean hasNext() {
        if(pElements==null){
            return false;
        }
        if(pElements.isEmpty()){
            try {
                next();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return pElements.size()>0;
    }

    @Override
    public long getSize() {
        return iSize.get();
    }

    @Override
    public long getDone() {
        return iFinish.get();
    }
}
