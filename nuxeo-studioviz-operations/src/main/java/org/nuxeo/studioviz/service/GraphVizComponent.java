/**
 * 
 */

package org.nuxeo.studioviz.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.service.CommandLineExecutorComponent;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.osgi.framework.Bundle;


/**
 * @author mgena
 */
public class GraphVizComponent extends DefaultComponent implements GraphVizService {

    protected Bundle bundle;
    public static final String GRAPHVIZ_FOLDER = "graphviz";
    
    public Bundle getBundle() {
        return bundle;
    }
    
    /**
     * Component activated notification. 
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component. 
     * <p>
     * The default implementation of this method is storing the Bundle owning that component in a class field.
     * You can use the bundle object to lookup for bundle resources:
     * <code>URL url = bundle.getEntry("META-INF/some.resource");</code>, load classes or to interact with OSGi framework.
     * <p> 
     * Note that you must always use the Bundle to lookup for resources in the bundle. Do not use the classloader for this.
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void activate(ComponentContext context) {
        this.bundle = context.getRuntimeContext().getBundle(); 
    }

    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *  
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void deactivate(ComponentContext context) {
        this.bundle = null;
    }

    /**
     * Application started notification.
     * Called after the application started. 
     * You can do here any initialization that requires a working application 
     * (all resolved bundles and components are active at that moment) 
     * 
     * @param context the component context. Use it to get the current bundle context
     */
    @Override
    public void applicationStarted(ComponentContext context) {
        // do nothing by default. You can remove this method if not used.
    }
    
    /**
     * 
     * @param blob containing the dot file
     * @param inputFileName the dot file name
     * @param outputFileName the output file name
     * @param format the format of the generation (png, cmapx etc.)
  
     * @throws CommandNotAvailable
     * @throws IOException 
     */
    public String generate(Blob blob, String inputFileName, String outputFileName, String format) throws CommandNotAvailable, IOException{
    	
    	//Create temporary directory
    	String tmpDir = Environment.getDefault().getTemp().getPath();
    	Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
    	Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, GRAPHVIZ_FOLDER)
    	                   : Framework.createTempDirectory(null);

    	Path inputFilePath = Paths.get(outDirPath.toString(), inputFileName);
    	Path outputFilePath = Paths.get(outDirPath.toString(), outputFileName);
    	
    	blob.transferTo(new File(inputFilePath.toString()));
    	
    	CommandLineExecutorComponent commandLineExecutorComponent = new CommandLineExecutorComponent();   	
    	CmdParameters parameters = new CmdParameters();    	
	    parameters.addNamedParameter("inputFile", inputFilePath.toString());
	    parameters.addNamedParameter("format", format);
	    parameters.addNamedParameter("outputFile", outputFilePath.toString());
	    commandLineExecutorComponent.execCommand("dot", parameters);	 
	    
	    return outputFilePath.toString();
    }
    
}
