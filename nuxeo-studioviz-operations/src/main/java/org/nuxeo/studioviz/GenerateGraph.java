/**
 *
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.security.CodeSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
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
    		
    	GraphHelper gh = new GraphHelper();
    	StudioVizService svs = Framework.getService(StudioVizService.class);
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
		    
		    mapModelJson = svs.generateModelGraphFromXML(url, null);
		    mapViewJson = svs.generateViewGraphFromXML(url, null);
		    businessRulesJson = svs.generateBusinessRulesGraphFromXML(url, null);
	    } catch (Exception e) {
	      logger.error("Exception while ",e);
	    }
    
    	String businessRules = new Gson().toJson(businessRulesJson);
    	String mapView = new Gson().toJson(mapViewJson);
    	String mapModel = new Gson().toJson(mapModelJson);
		return new StringBlob("{\"model\":"+mapModel+", \"view\": "+mapView+", \"businessRules\": "+businessRules+"}");		
    }

}
