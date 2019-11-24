package org.nuxeo.studioviz.test;

import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.studioviz.service.StudioVizService;

import com.google.gson.JsonObject;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy({"org.nuxeo.ecm.platform.commandline.executor", "org.nuxeo.connect.client","qualitified-studioviz"})

public class TestGenerateGraph {
	
	@Inject
	StudioVizService studiovizservice;
	
	@Test
    public void testGenerateModelGraphFromXML() throws Exception {
		String studiovizFolderPath = FileUtils.getResourcePathFromContext("GraphViz/mgena-SANDBOX.jar");
		JsonObject json = studiovizservice.generateModelGraphFromXML(studiovizFolderPath, null);
		assertTrue("img file not generated",json.has("img"));		
		assertTrue("img file not generated",json.has("map"));			
	}
	
	@Test
    public void testGenerateViewGraphFromXML() throws Exception {
		String studiovizFolderPath = FileUtils.getResourcePathFromContext("GraphViz/mgena-SANDBOX.jar");
		JsonObject json = studiovizservice.generateViewGraphFromXML(studiovizFolderPath, null);
		assertTrue("img file not generated",json.has("img"));		
		assertTrue("img file not generated",json.has("map"));			
	}
	
	@Test
    public void testGenerateBusinessRulesGraphFromXML() throws Exception {
		String studiovizFolderPath = FileUtils.getResourcePathFromContext("GraphViz/mgena-SANDBOX.jar");
		JsonObject json = studiovizservice.generateBusinessRulesGraphFromXML(studiovizFolderPath, null);
		assertTrue("img file not generated",json.has("img"));		
		assertTrue("img file not generated",json.has("map"));			
	}
	
	@Test
    public void testGenerateModelTestFromXML() throws Exception {
		String studiovizFolderPath = FileUtils.getResourcePathFromContext("GraphViz/mgena-SANDBOX.jar");
		Blob blob = studiovizservice.generateModelTextFromXML(studiovizFolderPath, null);
		assertTrue("Blob file not generated",blob.getLength()>0);
	}
}
