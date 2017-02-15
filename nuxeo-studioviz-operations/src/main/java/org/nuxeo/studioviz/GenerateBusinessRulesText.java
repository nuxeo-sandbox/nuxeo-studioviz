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
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.studioviz.helper.GraphHelper;
import org.nuxeo.studioviz.service.StudioVizService;


/**
 * @author mgena
 */
@Operation(id=GenerateBusinessRulesText.ID, category=Constants.CAT_EXECUTION, label="GenerateBusinessRulesText", description="")
public class GenerateBusinessRulesText {

    public static final String ID = "GenerateBusinessRulesText";
    private Log logger = LogFactory.getLog(GenerateBusinessRulesText.class);

    Blob blob = null;
    @OperationMethod
    public Blob run() {
    	String studioJar = "";
    	
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
		    
		    blob = svs.generateBusinessRulesTextFromXML(url, null);
		   
	    } catch (Exception e) {
	      logger.error("Exception while ",e);
	    }

    	return blob;
    }

}
