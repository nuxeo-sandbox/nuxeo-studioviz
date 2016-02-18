package org.nuxeo.studioviz.test;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.commandline.executor.service.CommandLineExecutorComponent;
import org.nuxeo.studioviz.GenerateGraph;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@RunWith(FeaturesRunner.class)
@Deploy({"nuxeo-graphviz-operations"})
@LocalDeploy({ "nuxeo-graphviz-operations:graphvizcmd.xml" })
public class TestGenerateGraph {
	
	@Test
    public void testExtractXMLFromStudioJar() throws Exception {
		/*GenerateGraph gg = new GenerateGraph();
		String graphVizFolderPath = getClass().getResource("/GraphViz").getFile();		
		gg.extractXMLFromStudioJar("mgena-SANDBOX.jar", graphVizFolderPath);
		File xml = new File(graphVizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml");
		assertTrue(xml.exists());*/
	}
	
	@Test
    public void testGenerateGraphFromXML() throws Exception {
		/*GenerateGraph gg = new GenerateGraph();
		String graphVizFolderPath = getClass().getResource("/GraphViz").getFile();		
		gg.extractXMLFromStudioJar("mgena-SANDBOX.jar", graphVizFolderPath);
		CommandLineExecutorComponent commandLineExecutorComponent = new CommandLineExecutorComponent();
		gg.generateModelGraphFromXML("mgena-SANDBOX", graphVizFolderPath, graphVizFolderPath, commandLineExecutorComponent);
		File dot = new File(graphVizFolderPath+File.separator+"inputModel.dot");
		assertTrue("input.dot file not generated",dot.exists());		
		File png = new File(graphVizFolderPath+File.separator+"imgModel.png");
		assertTrue("img.png file not generated",png.exists());
		File cmapx = new File(graphVizFolderPath+File.separator+"imgModel.cmapx");
		assertTrue("img.cmapx file not generated",cmapx.exists());*/
	}
}
