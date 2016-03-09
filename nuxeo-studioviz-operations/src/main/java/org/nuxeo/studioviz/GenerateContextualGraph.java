/**
 * 
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
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
@Operation(id=GenerateContextualGraph.ID, category=Constants.CAT_EXECUTION, label="GenerateContextualGraph", description="")
public class GenerateContextualGraph {

    public static final String ID = "GenerateContextualGraph";
    private Log logger = LogFactory.getLog(GenerateContextualGraph.class);
    
    @Param(name = "graphType")
    protected String graphType;
    
    @Param(name = "nodes")
    protected String nodes;

    @OperationMethod
    public Blob run() {
       	String studioJar = "";
       	JsonObject mapModelJson = new JsonObject();
    	JsonObject mapViewJson = new JsonObject();
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
		    
		    List<String> nodeList = new ArrayList<String>();
		    if(!("").equals(nodes)){
		    	String [] nodeArray = nodes.split(",");		    
		    	nodeList = Arrays.asList(nodeArray);
		    }
		    if(("model").equals(graphType)){
		    	mapModelJson = gh.generateModelGraphFromXML(studioProjectName, studiovizFolderPath, commandLineExecutorComponent, nodeList);
		    }else if(("view").equals(graphType)){
		    	mapViewJson = gh.generateViewGraphFromXML(studioProjectName, studiovizFolderPath, commandLineExecutorComponent, nodeList);
		    }else if("businessRules".equals(graphType)){
		    	businessRulesJson = gh.generateBusinessRulesGraphFromXML(studioProjectName, studiovizFolderPath, commandLineExecutorComponent, nodeList);
		    }		    
	    } catch (Exception e) {
	      logger.error("Exception while ",e);
	    }
    	String businessRules = new Gson().toJson(businessRulesJson);
        String mapView = new Gson().toJson(mapViewJson);
        String mapModel = new Gson().toJson(mapModelJson);
		return new StringBlob("{\"model\":"+mapModel+", \"view\": "+mapView+", \"businessRules\": "+businessRules+"}"); 
    }    

}
