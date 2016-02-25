/**
 * 
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.CodeSource;
import java.util.Arrays;
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
    	String mapModel = "";
    	String mapView = "";
    	String mapBusinessRules = "";
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
		    String destinationPath = nuxeoHomePath+File.separator+"nxserver"+File.separator+"nuxeo.war"+File.separator+"studioviz";
		    String [] nodeArray = nodes.split(",");
		    List<String> nodeList = Arrays.asList(nodeArray);
		    if(("model").equals(graphType)){
		    	mapModel = gh.generateModelGraphFromXML(studioProjectName, destinationPath, studiovizFolderPath, commandLineExecutorComponent, nodeList);
		    }else if(("view").equals(graphType)){
		    	mapView = gh.generateViewGraphFromXML(studioProjectName, destinationPath, studiovizFolderPath, commandLineExecutorComponent, nodeList);
		    }else if("businessRules".equals(graphType)){
		    	mapBusinessRules = gh.generateBusinessRulesGraphFromXML(studioProjectName, destinationPath, studiovizFolderPath, commandLineExecutorComponent, nodeList);
		    }		    
	    } catch (Exception e) {
	      logger.error("Exception while ",e);
	    }
    	try {
			return new StringBlob("{\"model\":\""+URLEncoder.encode(mapModel,"UTF-8")+"\", \"view\": \""+URLEncoder.encode(mapView,"UTF-8")+"\", \"businessRules\": \""+URLEncoder.encode(mapBusinessRules,"UTF-8")+"\"}");
		} catch (UnsupportedEncodingException e) {
			logger.error("Error while encoding result", e);
			return null;
		} 
    }    

}
