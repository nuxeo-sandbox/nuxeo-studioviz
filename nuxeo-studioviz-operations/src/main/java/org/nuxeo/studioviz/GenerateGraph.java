/**
 *
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.CodeSource;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.platform.commandline.executor.service.CommandLineExecutorComponent;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.studioviz.helper.GraphHelper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;


/**
 * @author mgena
 */
@Operation(id=GenerateGraph.ID, category=Constants.CAT_EXECUTION, label="GenerateGraph", description="")
public class GenerateGraph {

    public static final String ID = "GenerateGraph";
    private Log logger = LogFactory.getLog(GenerateGraph.class);

    @OperationMethod
    public Blob run() {
    	String studioJar = "";
    	JsonObject  mapModelJson = new JsonObject();
    	JsonObject  mapViewJson = new JsonObject();
    	JsonObject businessRulesJson = new JsonObject();
    	String url = "";
    	CommandLineExecutorComponent commandLineExecutorComponent = new CommandLineExecutorComponent();
    	String nuxeoHomePath = Environment.getDefault().getServerHome().getAbsolutePath();
    	
    	GraphHelper gh = new GraphHelper();
    	
    	try {
		    studioJar = gh.getStudioJar();

		    //build the studio jar path
		    CodeSource src = Framework.class.getProtectionDomain().getCodeSource();
		    if (src != null) {
		    	url = src.getLocation().toString();
		    	String path[] = url.split(File.separator);
		    	url = url.replace(path[path.length-1], studioJar);
		    	url = url.replace("file:","");
		    }

		    gh.copyStudioJar(url, studioJar, nuxeoHomePath, commandLineExecutorComponent);
		    String studiovizFolderPath = nuxeoHomePath+File.separator+"studioviz";
		    gh.extractXMLFromStudioJar(studioJar, studiovizFolderPath);
		    String studioProjectName = studioJar.replace(".jar", "");

		    mapModelJson = gh.generateModelGraphFromXML(studioProjectName, studiovizFolderPath, commandLineExecutorComponent, null);

		    mapViewJson = gh.generateViewGraphFromXML(studioProjectName, studiovizFolderPath, commandLineExecutorComponent, null);

		    businessRulesJson = gh.generateBusinessRulesGraphFromXML(studioProjectName, studiovizFolderPath, commandLineExecutorComponent, null);

	    } catch (Exception e) {
	      logger.error("Exception while ",e);
	    }
    	ArrayList<String> automationList = gh.getAutomationList();
    	String json = new Gson().toJson(automationList);
    	String businessRules = new Gson().toJson(businessRulesJson);
    	String mapView = new Gson().toJson(mapViewJson);
    	String mapModel = new Gson().toJson(mapModelJson);
		return new StringBlob("{\"model\":"+mapModel+", \"view\": "+mapView+", \"businessRules\": "+businessRules+", \"automationList\": "+json+"}");		
    }

}
