/**
 *
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.security.CodeSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
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

	@Context
	protected CoreSession session;

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
		    
		    mapModelJson = svs.generateModelGraphFromXML(url, null);
		    mapViewJson = svs.generateViewGraphFromXML(url, null);
		    businessRulesJson = svs.generateBusinessRulesGraphFromXML(url, null);
	    } catch (Exception e) {
	      logger.error("Exception while ",e);
	    }
    
    	String businessRules = new Gson().toJson(businessRulesJson);
    	String mapView = new Gson().toJson(mapViewJson);
    	String mapModel = new Gson().toJson(mapModelJson);

    	//DocumentModel doc = session.createDocumentModel("File");
		//doc.setPropertyValue("dc:description","{\"model\":"+mapModel+", \"view\": "+mapView+", \"businessRules\": "+businessRules+"}" );
		return new StringBlob("{\"model\":"+mapModel+", \"view\": "+mapView+", \"businessRules\": "+businessRules+"}", "application/json");
		//return doc;
    }

}
