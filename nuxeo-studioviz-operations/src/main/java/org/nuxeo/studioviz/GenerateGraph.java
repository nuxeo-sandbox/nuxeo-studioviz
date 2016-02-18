/**
 *
 */

package org.nuxeo.studioviz;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.connect.data.DownloadablePackage;
import org.nuxeo.connect.packages.PackageManager;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.service.CommandLineExecutorComponent;
import org.nuxeo.jaxb.Component;
import org.nuxeo.jaxb.Component.Extension;
import org.nuxeo.jaxb.Component.Extension.Action;
import org.nuxeo.jaxb.Component.Extension.Action.Filter;
import org.nuxeo.jaxb.Component.Extension.Action.Filter.Rule;
import org.nuxeo.jaxb.Component.Extension.Action.Filter.Rule.Type;
import org.nuxeo.jaxb.Component.Extension.Chain;
import org.nuxeo.jaxb.Component.Extension.Doctype;
import org.nuxeo.jaxb.Component.Extension.Handler;
import org.nuxeo.jaxb.Component.Extension.Schema;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews.ContentView;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts.Layout;
import org.nuxeo.runtime.api.Framework;


/**
 * @author mgena
 */
@Operation(id=GenerateGraph.ID, category=Constants.CAT_NOTIFICATION, label="GenerateGraph", description="")
public class GenerateGraph {

    public static final String ID = "GenerateGraph";
    private Log logger = LogFactory.getLog(GenerateGraph.class);
    public static final String SNAPSHOT_SUFFIX = "0.0.0-SNAPSHOT";
    public static final String EXTENSIONPOINT_CHAIN = "chains";
    public static final String EXTENSIONPOINT_EVENT_HANDLERS = "event-handlers";
    public static final String EXTENSIONPOINT_ACTIONS = "actions";
    public static final String EXTENSIONPOINT_SCHEMAS = "schema";
    public static final String EXTENSIONPOINT_DOCTYPE = "doctype";
    public static final String EXTENSIONPOINT_TYPES = "types";
    public static final String COMMON_SCHEMAS = "common,dublincore,uid,task,file,picture,image_metadata,iptc,publishing,webcontainer,files";



    @OperationMethod
    public Blob run() {
    	String studioJar = "";
    	String mapModel = "";
    	String mapView = "";
    	String mapBusinessRules = "";
    	String url = "";
    	CommandLineExecutorComponent commandLineExecutorComponent = new CommandLineExecutorComponent();
    	String nuxeoHomePath = Environment.getDefault().getServerHome().getAbsolutePath();
    	try {
		    studioJar = getStudioJar();

		    //build the studio jar path
		    CodeSource src = Framework.class.getProtectionDomain().getCodeSource();
		    if (src != null) {
		    	url = src.getLocation().toString();
		    	String path[] = url.split("/");
		    	url = url.replace(path[path.length-1], studioJar);
		    	url = url.replace("file:","");
		    }

		    copyStudioJar(url, studioJar, nuxeoHomePath, commandLineExecutorComponent);
		    String studiovizFolderPath = nuxeoHomePath+File.separator+"studioviz";
		    extractXMLFromStudioJar(studioJar, studiovizFolderPath);
		    String studioProjectName = studioJar.replace(".jar", "");
		    String destinationPath = nuxeoHomePath+File.separator+"nxserver"+File.separator+"nuxeo.war"+File.separator+"studioviz";

		    mapModel = generateModelGraphFromXML(studioProjectName, destinationPath, studiovizFolderPath, commandLineExecutorComponent);

		    mapView = generateViewGraphFromXML(studioProjectName, destinationPath, studiovizFolderPath, commandLineExecutorComponent);

		    mapBusinessRules = generateBusinessRulesGraphFromXML(studioProjectName, destinationPath, studiovizFolderPath, commandLineExecutorComponent);

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


    public static boolean isSnapshot(DownloadablePackage pkg) {
		return ((pkg.getVersion() != null) && (pkg.getVersion().toString().endsWith("0.0.0-SNAPSHOT")));
	}

	public static DownloadablePackage getSnapshot(List<DownloadablePackage> pkgs) {
		for (DownloadablePackage pkg : pkgs) {
			if (isSnapshot(pkg)) {
				return pkg;
			}
		}
		return null;
	}

	public void writeToFile(String path, String content) {
		FileOutputStream fop = null;
		File file;
		try {
			file = new File(path);
			fop = new FileOutputStream(file);

			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
			// get the content in bytes
			byte[] contentInBytes = content.getBytes();

			fop.write(contentInBytes);
			fop.flush();
			fop.close();

		} catch (IOException e) {
			logger.error("Error while writing into file ", e);
		} finally {
			try {
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				logger.error("Error while writing into file ", e);
			}
		}
	}

	public static String cleanUpForDot(String content){
		 content = content.replaceAll("\\.", "");
		 content = content.replaceAll("\\/", "");
		 content = content.replaceAll("\\-", "_");
		 //content = content.replaceAll(".", "");
		 return content;
	}

	public String getStudioJar(){
		String studioJar = "";
		PackageManager pm = Framework.getLocalService(PackageManager.class);
	    List<DownloadablePackage> pkgs = pm.listRemoteAssociatedStudioPackages();
	    DownloadablePackage snapshotPkg = getSnapshot(pkgs);
	    String studioPackage = "";
	    if (snapshotPkg != null) {
	    	studioPackage = snapshotPkg.getId();
	    	studioJar = studioPackage.replace("-0.0.0-SNAPSHOT", "")+".jar";
	    } else {
	    	logger.info("No Studio Package found.");
	    }
	    return studioJar;
	}

	public void copyStudioJar(String url, String studioJar, String nuxeoHomePath, CommandLineExecutorComponent commandLineExecutorComponent) throws CommandNotAvailable, IOException{
    	//Create the studioviz folder if it doesn't exist
    	File dir = new File(nuxeoHomePath+File.separator+"studioviz");
    	if(!dir.exists()) {
    		try{
    			dir.mkdir();
    	    }
    	    catch(SecurityException se){
    	       logger.error("Error while creating the directory [studioviz]", se);
    	    }
    	}

        CmdParameters params2 = new CmdParameters();
        params2.addNamedParameter("studioJar", url);
        params2.addNamedParameter("dest", nuxeoHomePath+File.separator+"studioviz"+File.separator+studioJar);
        commandLineExecutorComponent.execCommand("copy-studio-jar", params2);
	}

	public void extractXMLFromStudioJar(String studioJar, String studiovizFolderPath) throws CommandNotAvailable, IOException{
		Runtime rt = Runtime.getRuntime();
	    String[] cmd = { "/bin/sh", "-c", "cd "+studiovizFolderPath+"; jar xf "+studioJar };    
	    Process p = rt.exec(cmd);
	    try {
			p.waitFor();
		} catch (InterruptedException e) {
			logger.error("Error while waiting for the studio jar extraction", e);
		}
	}


	public String generateModelGraphFromXML(String studioProjectName, String destinationPath, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent) throws JAXBException, CommandNotAvailable, IOException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String result = "";
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		String schemas = "subgraph cluster_0 {\n"+
	 		 	   "	node [style=filled];\n"+
	 		 	   " 	label = \"Schemas\";\n"+
	 		 	   "  	color=\"#24A4CC\";\n";

		String docTypes = "subgraph cluster_1 {\n"+
	 		 	   "	node [style=filled];\n"+
	 		 	   " 	label = \"Document Types\";\n"+
	 		 	   "  	color=\"#1CA5FC\";\n";

		String facets = "subgraph cluster_2 {\n"+
	 		 	   "	node [style=filled];\n"+
	 		 	   " 	label = \"Facets\";\n"+
	 		 	   "  	color=\"#17384E\";\n";

		result = "digraph M {\n"+
		    "graph [fontname = \"helvetica\", fontsize=11];\n"+
		    "node [fontname = \"helvetica\", fontsize=11];\n"+
		    "edge [fontname = \"helvetica\", fontsize=11];\n";
		List<Extension> extensions = component.getExtension();

		int nbSchemas = 0;
		int nbDocTypes = 0;
		int nbFacets = 0;
		ArrayList<String> docTypesList = new ArrayList<String>();
		ArrayList<String> schemasList = new ArrayList<String>();
		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
	    		case EXTENSIONPOINT_SCHEMAS :
	    			try{
	    				List<Schema> schemaList = extension.getSchema();
	    				for(Schema schema : schemaList){
	    					String schemaName = schema.getName();
	    					//Schemas starting with var_ are reserved for worfklow tasks
	    					//Schemas ending with _cv are reserved for content views
	    					if(schemaName != null && !schemaName.startsWith("var_") && !schemaName.endsWith("_cv") && !schemasList.contains(schemaName)){
	    						result += schemaName+"_sh [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+schemaName+".ds\", label=\""+schemaName+"\",shape=box,fontcolor=white,color=\"#24A4CC\",fillcolor=\"#24A4CC\",style=\"filled\"];\n";
	    						if(nbSchemas > 0){
		    						schemas += "->";
		    					}
	    						schemas += schemaName+"_sh";
	    						schemasList.add(schemaName+"_sh");
	    						nbSchemas ++;
	    					}

	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting schemas", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_DOCTYPE :
	    			try{
	    				List<Doctype> docTypeList = extension.getDoctype();
	    				for(Doctype docType : docTypeList){
	    					String docTypeName = docType.getName();
	    					//DocType ending with _cv are created for content views
	    					if(docTypeName != null && !docTypeName.endsWith("_cv")){
	    						result += docTypeName+ " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+docTypeName+".doc\", label=\""+docTypeName+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    						result += docTypeName+"->"+docType.getExtends()+"[label=\"inherits\"];\n";

	    						List<Doctype.Schema> extraSchemas = docType.getSchema();
	    						for(Doctype.Schema extraSchema: extraSchemas){
	    							//Don't include common schemas for the sake of visibility
	    							if(!COMMON_SCHEMAS.contains(extraSchema.getName())){
	    								result += docTypeName+"->"+extraSchema.getName()+"_sh;\n";
	    								if(!schemasList.contains(extraSchema.getName()+"_sh")){
		    								result += extraSchema.getName()+"_sh [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+extraSchema.getName()+".ds\", label=\""+extraSchema.getName()+"\",shape=box,fontcolor=white,color=\"#24A4CC\",fillcolor=\"#24A4CC\",style=\"filled\"];\n";
		    	    						if(nbSchemas > 0){
		    		    						schemas += "->";
		    		    					}
		    	    						schemas += extraSchema.getName()+"_sh";
		    	    						schemasList.add(extraSchema.getName()+"_sh");
		    	    						nbSchemas ++;
	    								}
	    							}
	    						}

	    						List<Doctype.Facet> extraFacets = docType.getFacet();
	    						for(Doctype.Facet extraFacet : extraFacets){
	    							result += docTypeName+"->"+extraFacet.getName()+"_facet;\n";
	    							if(!facets.contains(extraFacet.getName()+"_facet")){
		    							result += extraFacet.getName()+ "_facet [label=\""+extraFacet.getName()+"\",shape=box,fontcolor=white,color=\"#17384E\",fillcolor=\"#17384E\",style=\"filled\"];\n";
		    							if(nbFacets >0){
		    								facets += "->";
		    							}
		    							facets += extraFacet.getName()+"_facet";
		    							nbFacets ++;
		    						}
	    						}

	    						if(nbDocTypes > 0){
		    						docTypes += "->";
		    					}
	    						docTypes += docTypeName;
	    						nbDocTypes ++;

	    						if(!docTypesList.contains(docType.getExtends())){
	    							result += docType.getExtends()+ " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+docType.getExtends()+".doc\", label=\""+docType.getExtends()+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    							docTypes += "->";
	    							docTypes += docType.getExtends();
	    							docTypesList.add(docType.getExtends());
	    							nbDocTypes ++;
	    						}
	    					}
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting document type", e);
	    			}
	    			break;
	    	}
	    }

		schemas += (nbSchemas>1?" [style=invis]":"")+";\n}";
		docTypes += (nbDocTypes>1?" [style=invis]":"")+";\n}";
		facets += (nbFacets>1?" [style=invis]":"")+";\n}";

	    result += (nbSchemas>0?schemas:"")+"\n"+(nbDocTypes>0?docTypes:"")+"\n"+(nbFacets>0?facets:"")+"\n";
    	result += "}";

	    writeToFile(studiovizFolderPath+File.separator+File.separator+"inputModel.dot", result);

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputModel.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgModel.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgModel.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(destinationPath+File.separator+"imgModel.cmapx"));
	    return map;
	}

	public String generateViewGraphFromXML(String studioProjectName, String destinationPath, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent) throws JAXBException, CommandNotAvailable, IOException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String result = "";
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		String tabs = "subgraph cluster_0 {\n"+
		 		 	   "	node [style=filled];\n"+
		 		 	   " 	label = \"Tabs\";\n"+
		 		 	   "  	color=\"#2B333E\";\n";

		String docTypes = "subgraph cluster_1 {\n"+
	 		 	   "	node [style=filled];\n"+
	 		 	   " 	label = \"Document Types\";\n"+
	 		 	   "  	color=\"#1CA5FC\";\n";

		String contentViews = "subgraph cluster_2 {\n"+
	 		 	   "	node [style=filled];\n"+
	 		 	   " 	label = \"Content Views\";\n"+
	 		 	   "  	color=\"#31A3C5\";\n";
		
		String formLayouts = "subgraph cluster_3 {\n"+
	 		 	   "	node [style=filled];\n"+
	 		 	   " 	label = \"Form Layouts\";\n"+
	 		 	   "  	color=\"#FC4835\";\n";

		result = "digraph V {\n"+
		    "graph [fontname = \"helvetica\", fontsize=11];\n"+
		    "node [fontname = \"helvetica\", fontsize=11];\n"+
		    "edge [fontname = \"helvetica\", fontsize=11];\n";
		List<Extension> extensions = component.getExtension();

		int nbTabs = 0;
		int nbDocTypes = 0;
		int nbContentViews = 0;
		int nbFormLayouts = 0;
		ArrayList<String> docTypesList = new ArrayList<String>();
		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
		    	case EXTENSIONPOINT_ACTIONS :
		    		try{
		    			List<Action> actions = extension.getAction();
		    			for(Action action:actions){
		    				String linkType = "";
		    				try{
		    					linkType = action.getType();
		    					//handle the rest_document_link types as Tabs
		    					if(linkType == null || !(linkType).equals("rest_document_link")){
		    						continue;
		    					}

		    				}catch(Exception e){
		    					logger.error("Error when getting chainId", e);
		    				}
		    				String cleanedActionId = cleanUpForDot(action.getId());

		    				Filter filter = action.getFilter();
		    				if(filter != null){
		    					List<Rule> rules = filter.getRule();
		    					if(rules != null){
		    						for(Rule rule: rules){
		    							if("true".equals(rule.getGrant())){
		    								List<Type> types = rule.getType();
		    								if(types !=null){
		    									for(Type type:types){
		    										String docTypeName = type.getValue();
		    					    				result += cleanedActionId+"_tab -> "+docTypeName+";\n";

		    					    				if(!docTypesList.contains(docTypeName)){
		    					    					result += docTypeName+ " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+docTypeName+".doc\", label=\""+docTypeName+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
		    					    					if(nbDocTypes >0){
		    					    						docTypes += "->";
		    					    					}
		    		    								docTypes += docTypeName;
		    		    								docTypesList.add(docTypeName);
		    		    								nbDocTypes ++;
		    					    				}

		    									}
		    								}
		    							}
		    						}
		    					}
		    				}


		    				if(!tabs.contains(cleanedActionId)){
		    					result += cleanedActionId + "_tab [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+action.getId()+".tab\" label=\""+action.getId()+"\",shape=box,fontcolor=white,color=\"#2B333E\",fillcolor=\"#2B333E\",style=\"filled\"];\n";
		    					if(nbTabs >0){
			    					tabs += "->";
		    					}
		    					tabs += cleanedActionId+"_tab";
		    					nbTabs ++;
		    				}
		    			}
		    		}catch(Exception e){
		    			logger.error("Error when getting Actions", e);
		    		}
		    		break;

	    		case EXTENSIONPOINT_TYPES :
	    			try{
	    				List<org.nuxeo.jaxb.Component.Extension.Type> typeList = extension.getType();
	    				for(org.nuxeo.jaxb.Component.Extension.Type type : typeList){
	    					String typeId = type.getId();

	    					List<ContentViews> contentViewsList= type.getContentViews();
	    					if(contentViewsList != null){
	    						for(ContentViews cvs : contentViewsList){
	    							ContentView contentView = cvs.getContentView();
	    							if("content".equals(cvs.getCategory())){

	    								if(!docTypesList.contains(typeId)){
	    			    					result += typeId+ " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+typeId+".doc\", label=\""+typeId+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    			    					if(nbDocTypes >0){
	    			    						docTypes += "->";
	    			    					}
	    									docTypes += typeId;
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}

	    								String contentViewName = contentView.getValue();
	    								result += typeId+"->"+contentViewName+";\n";
	    								if(!contentViews.contains(contentViewName)){
	    									result += contentViewName+ " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+contentViewName+".contentView\", label=\""+contentViewName+"\",shape=box,fontcolor=white,color=\"#31A3C5\",fillcolor=\"#31A3C5\",style=\"filled\"];\n";
		    		    					if(nbContentViews >0){
		    		    						contentViews += "->";
		    		    					}
		    		    					contentViews += contentViewName;
		    		    					nbContentViews ++;
	    								}
	    							}
	    						}
	    					}
	    						    					
	    					//Handle Form Layouts
	    					List<Layouts> layoutList = type.getLayouts();
	    					for(Layouts layouts: layoutList){
	    						Layout layout = layouts.getLayout();
	    						if(!layout.getValue().startsWith("layout@") && !typeId.endsWith("_cv")){
	    							String formLayoutName = layout.getValue().split("@")[0];	    							
	    							if(!formLayouts.contains(formLayoutName+"_fl")){
	    								
	    								if(!docTypesList.contains(typeId)){
	    			    					result += typeId+ " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+typeId+".doc\", label=\""+typeId+"\",shape=box,fontcolor=white,color=\"#1CA5FC\",fillcolor=\"#1CA5FC\",style=\"filled\"];\n";
	    			    					if(nbDocTypes >0){
	    			    						docTypes += "->";
	    			    					}
	    									docTypes += typeId;
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}
	    								
	    								result += typeId+"->"+formLayoutName+"_fl;\n";
    									result += formLayoutName+ "_fl [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+formLayoutName+".layout\", label=\""+formLayoutName+"\",shape=box,fontcolor=white,color=\"#FC4835\",fillcolor=\"#FC4835\",style=\"filled\"];\n";
	    		    					if(nbFormLayouts >0){
	    		    						formLayouts += "->";
	    		    					}
	    		    					formLayouts += formLayoutName+"_fl";
	    		    					nbFormLayouts ++;
    								}
	    						}
	    					}
	    					
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting document type", e);
	    			}
	    			break;
	    	}
	    }

		tabs += (nbTabs>1?" [style=invis]":"")+";\n}";
		docTypes += (nbDocTypes>1?" [style=invis]":"")+";\n}";
		contentViews +=  (nbContentViews>1?" [style=invis]":"")+";\n}";
		formLayouts +=  (nbFormLayouts>1?" [style=invis]":"")+";\n}";
	    result += (nbTabs>0?tabs:"")+"\n"+(nbDocTypes>0?docTypes:"")+"\n"+(nbContentViews>0?contentViews:"")+"\n"+(nbFormLayouts>0?formLayouts:"")+"\n";
    	result += "}";

	    writeToFile(studiovizFolderPath+File.separator+File.separator+"inputView.dot", result);

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputView.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgView.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgView.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(destinationPath+File.separator+"imgView.cmapx"));
	    return map;
	}

	public String generateBusinessRulesGraphFromXML(String studioProjectName, String destinationPath, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent) throws JAXBException, CommandNotAvailable, IOException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String result = "";
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		String userActions = "subgraph cluster_0 {\n"+
						 	 "	node [style=filled];\n"+
						     "	label = \"User Actions\";\n"+
						     "  color=\"#00ADFF\";\n";

		String automationChainsAndScripting = "subgraph cluster_1 {\n"+
				 							  "	node [style=filled];\n"+
				 							  " label = \"Automation Chains & Scriptings\";\n"+
				 							  " color=\"#28A3C7\";\n";

		String events =  "subgraph cluster_2 {\n"+
				 		 "	node [style=filled];\n"+
				 		 "  label = \"Events\";\n"+
				 		 " 	color=\"#FF462A\";\n";

		result = "digraph BL {\n"+
		    "graph [fontname = \"helvetica\", fontsize=11];\n"+
		    "node [fontname = \"helvetica\", fontsize=11];\n"+
		    "edge [fontname = \"helvetica\", fontsize=11];\n";
		List<Extension> extensions = component.getExtension();
		String pattern = "\\#\\{operationActionBean.doOperation\\('(.*)'\\)\\}";
		// Create a Pattern object
		Pattern r = Pattern.compile(pattern);
		int nbUserActions = 0;
		int nbAutomationChains = 0;
		int nbAutomationScripting = 0;
		int nbEvents = 0;

		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
		    	case EXTENSIONPOINT_ACTIONS :
		    		try{
		    			List<Action> actions = extension.getAction();
		    			for(Action action:actions){
		    				String chainId = "";
		    				String linkType = "";
		    				try{
		    					chainId = action.getLink();
		    					linkType = action.getType();
		    					//TODO handle the rest_document_link types, Tabs?
		    					if(chainId == null){
		    						continue;
		    					}
		    					// Now create matcher object.
		    				    Matcher m = r.matcher(chainId);
		    				    if (m.find( )) {
		    				    	chainId = m.group(1);
		    				    }
		    				}catch(Exception e){
		    					logger.error("Error when getting chainId", e);
		    				}
		    				String cleanedActionId = cleanUpForDot(action.getId());

		    				if(chainId != null && !("").equals(chainId) && !(".").equals(chainId)  && !chainId.endsWith("xhtml")){
		    					String cleanedChainId = cleanUpForDot(chainId);
		    					String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";
		    					result += cleanedActionId+"_action -> "+cleanedChainId+";\n";

			    				if(!automationChainsAndScripting.contains(cleanedChainId)){
			    					result += cleanedChainId + " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+refChainId+"\", label=\""+chainId+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
			    					if(nbAutomationChains >0 || nbAutomationScripting >0){
			    						automationChainsAndScripting += "->";
				    				}

			    					automationChainsAndScripting += cleanedChainId;
			    					if(chainId.startsWith("javascript")){
				    					nbAutomationScripting ++;
				    				}else{
				    					nbAutomationChains ++;
				    				}
			    				}
			    				result += cleanedActionId+"_action [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+action.getId()+".action\", label=\""+action.getId()+"\n"+(action.getLabel()!= null ? action.getLabel():"")+"\",shape=box,fontcolor=white,color=\"#00ADFF\",fillcolor=\"#00ADFF\",style=\"filled\"];\n";
				    			if(nbUserActions >0){
				    				userActions += "->";
				    			}
				    			userActions += cleanedActionId+"_action";
				    			nbUserActions ++;
		    				}

		    			}
		    		}catch(Exception e){
		    			logger.error("Error when getting Actions", e);
		    		}
		    		break;
		    	case EXTENSIONPOINT_CHAIN :
		    		try{
		    			List<Chain> chains = extension.getChain();
		    			for(Chain chain:chains){
		    				String chainId = chain.getId();
		    				String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";

		    				String chainIdForDot = cleanUpForDot(chain.getId());

		    				String description = (chain.getDescription() != null ? "\n"+chain.getDescription():"");
		    				description = description.replace("\"", "\\\"");
	    					result += chainIdForDot + " [URL=\"https://connect.nuxeo.com/nuxeo/site/studio/ide?project="+studioProjectName+"#@feature:"+refChainId+"\", label=\""+chainId+description+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";

		    				//handle the link between Automation chains
	    					//TODO handle the link between Automation chains & scripting
	    					if(chain.getOperation() != null){
	    						for(org.nuxeo.jaxb.Component.Extension.Chain.Operation operation:chain.getOperation()){
	    							if(("RunOperation").equals(operation.getId())){
	    								for(org.nuxeo.jaxb.Component.Extension.Chain.Operation.Param param : operation.getParam()){
	    									if(("id").equals(param.getName())){
	    										if(param.getValue().contains(":")){
	    											String exprPattern = "*\"(.*)\":\"(.*)";
	    											Pattern expR = Pattern.compile(exprPattern);
	    											Matcher m = expR.matcher(param.getValue());
	    						    				if (m.find( )) {
	    						    					result += chainIdForDot+" -> "+cleanUpForDot(m.group(1))+";\n";
	    						    					result += chainIdForDot+" -> "+cleanUpForDot(m.group(2))+";\n";
	    						    				}
	    										}else{
	    											result += chainIdForDot+" -> "+cleanUpForDot(param.getValue())+";\n";
	    											if(!automationChainsAndScripting.contains(cleanUpForDot(param.getValue()))){
		    											if(nbAutomationChains >0 || nbAutomationScripting >0){
		    					    						automationChainsAndScripting += "->";
		    						    				}

		    					    					automationChainsAndScripting += cleanUpForDot(param.getValue());
		    					    					if(chainId.startsWith("javascript")){
		    						    					nbAutomationScripting ++;
		    						    				}else{
		    						    					nbAutomationChains ++;
		    						    				}
	    											}

	    										}
	    									}
	    								}
	    							}
	    						}
	    					}
	    					if(nbAutomationChains >0 || nbAutomationScripting >0){
	    						automationChainsAndScripting += "->";
		    				}

	    					automationChainsAndScripting += chainIdForDot;
	    					if(chainId.startsWith("javascript")){
		    					nbAutomationScripting ++;
		    				}else{
		    					nbAutomationChains ++;
		    				}
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting Chains", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_EVENT_HANDLERS :
	    			try{
	    				List<Handler> handlers = extension.getHandler();
	    				for(Handler handler:handlers){
	    					handler.getChainId();
	    					String chainIdForDot = cleanUpForDot(handler.getChainId());

	    					result += chainIdForDot+"_handler"+ " [label=\""+handler.getChainId()+"_handler\",shape=box,fontcolor=white,color=\"#FF462A\",fillcolor=\"#FF462A\",style=\"filled\"];\n";
	    					result += chainIdForDot+ " [label=\""+handler.getChainId()+"\",shape=box,fontcolor=white,color=\"#28A3C7\",fillcolor=\"#28A3C7\",style=\"filled\"];\n";
	    					result += chainIdForDot+"_handler"+" -> "+chainIdForDot+";\n";

	    					if(nbEvents > 0){
	    						events += "->";
	    					}
	    					events += cleanUpForDot(handler.getChainId())+"_handler";
	    					nbEvents ++;

	    					if(!automationChainsAndScripting.contains(chainIdForDot)){
		    					if(nbAutomationChains >0 || nbAutomationScripting >0){
		    						automationChainsAndScripting += "->";
			    				}

		    					automationChainsAndScripting += chainIdForDot;
		    					if(chainIdForDot.startsWith("javascript")){
			    					nbAutomationScripting ++;
			    				}else{
			    					nbAutomationChains ++;
			    				}
		    				}


	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting Event Handlers", e);
	    			}
	    			break;
	    	}
	    }

		userActions += (nbUserActions>1?" [style=invis]":"")+";\n}";
		automationChainsAndScripting += (nbAutomationChains>1?" [style=invis]":"")+";\n}";
		events += (nbEvents>1?" [style=invis]":"")+";\n}";


	    result += (nbUserActions>0 ? userActions: "")+"\n"+((nbAutomationChains+ nbAutomationScripting >0)? automationChainsAndScripting:"")+"\n"+(nbEvents>0? events: "")+"\n";
    	result += "}";

	    writeToFile(studiovizFolderPath+File.separator+File.separator+"inputBusinessRules.dot", result);

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputBusinessRules.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgBusinessRules.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", destinationPath+File.separator+"imgBusinessRules.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(destinationPath+File.separator+"imgBusinessRules.cmapx"));
	    return map;
	}



	/*public static void main(String[] args){
		try {
			generateViewGraphFromXML("cvs-demo-nuxeo", "/Users/mgena/Documents/studioviz/nuxeo-cap-8.1-tomcat/studioviz", "/Users/mgena/Documents/studioviz/nuxeo-cap-8.1-tomcat/studioviz", null);
		} catch (JAXBException | CommandNotAvailable | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/
}
