/**
 * 
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.studioviz.helper.GraphHelper;
import org.nuxeo.studioviz.service.StudioVizService;

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
 
    	GraphHelper gh = new GraphHelper();
    	StudioVizService svs = Framework.getService(StudioVizService.class);

    	try {
		    studioJar = gh.getStudioJar();
		    
		    //build the studio jar path
		    CodeSource src = Framework.class.getProtectionDomain().getCodeSource();
		    if (src != null) {
		    	url = src.getLocation().toString();
		    	String path[] = url.split(File.separator);
		    	url = url.replace(File.separator+path[path.length-1], "");
		    	url = url.replace("file:","");
		    	
		    	File folder = new File(url);
		    	File[] listOfFiles = folder.listFiles();

	    	    for (int i = 0; i < listOfFiles.length; i++) {
	    	      if (listOfFiles[i].isFile() && listOfFiles[i].getName().startsWith(studioJar.replace(".jar", ""))) {
	    	        url = url+File.separator+listOfFiles[i].getName();
	    	        break;
	    	      }
	    	    }	    		    	 	
		    }	
		    
		    List<String> nodeList = new ArrayList<String>();
		    if(!("").equals(nodes)){
		    	String [] nodeArray = nodes.split(",");		    
		    	nodeList = Arrays.asList(nodeArray);
		    }
		    if(("model").equals(graphType)){
		    	mapModelJson = svs.generateModelGraphFromXML(url, nodeList);
		    }else if(("view").equals(graphType)){
		    	mapViewJson = svs.generateViewGraphFromXML(url, nodeList);
		    }else if("businessRules".equals(graphType)){
		    	businessRulesJson = svs.generateBusinessRulesGraphFromXML(url, nodeList);
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
