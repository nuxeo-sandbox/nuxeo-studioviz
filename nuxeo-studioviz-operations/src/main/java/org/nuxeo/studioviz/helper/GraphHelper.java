package org.nuxeo.studioviz.helper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.connect.data.DownloadablePackage;
import org.nuxeo.connect.packages.PackageManager;
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
import org.nuxeo.jaxb.Component.Extension.TemplateResource;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews.ContentView;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts.Layout;
import org.nuxeo.runtime.api.Framework;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

public class GraphHelper {

    private Log logger = LogFactory.getLog(GraphHelper.class);
    private static final String SNAPSHOT_SUFFIX = "0.0.0-SNAPSHOT";
    private static final String EXTENSIONPOINT_CHAIN = "chains";
    private static final String EXTENSIONPOINT_EVENT_HANDLERS = "event-handlers";
    private static final String EXTENSIONPOINT_ACTIONS = "actions";
    private static final String EXTENSIONPOINT_SCHEMAS = "schema";
    private static final String EXTENSIONPOINT_DOCTYPE = "doctype";
    private static final String EXTENSIONPOINT_TYPES = "types";
    private static final String EXTENSIONPOINT_ROUTE_MODEL_IMPORTER = "routeModelImporter";
    private static final String COMMON_SCHEMAS = "common,dublincore,uid,task,file,picture,image_metadata,iptc,publishing,webcontainer,files";
    private static final String CONNECT_URL = "https://connect.nuxeo.com/nuxeo/site/studio/ide?project=";
    
    private ArrayList<String> automationList = new ArrayList<String>();
	
    private boolean isSnapshot(DownloadablePackage pkg) {
		return ((pkg.getVersion() != null) && (pkg.getVersion().toString().endsWith(SNAPSHOT_SUFFIX)));
	}

    private DownloadablePackage getSnapshot(List<DownloadablePackage> pkgs) {
		for (DownloadablePackage pkg : pkgs) {
			if (isSnapshot(pkg)) {
				return pkg;
			}
		}
		return null;
	}
	
	public ArrayList<String> getAutomationList(){
		Collections.sort(automationList);
		return automationList;
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

	private String cleanUpForDot(String content){
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
	    	studioJar = studioPackage.replace("-"+SNAPSHOT_SUFFIX, "")+".jar";
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

	public JsonObject generateModelGraphFromXML(String studioProjectName, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();

		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		//Freemarker configuration object
        Configuration cfg = new Configuration(new Version(2,3,0));
        
        //Load template
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        Template template = cfg.getTemplate("inputModel.ftl");       
        
        // Build the data-model
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("studioProjectName", studioProjectName);
		
        ArrayList<String> nodes = new ArrayList<String>();
        ArrayList<String> transitions = new ArrayList<String>();
        
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        
        String schemas = "";
        String docTypes = "";
        String facets = "";

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
	    						JsonObject schemaJson = new JsonObject();
	    						schemaJson.addProperty("name", schemaName+"_sh");
	    						schemaJson.addProperty("featureName", schemaName+".ds");
	    						schemaJson.addProperty("labelName", schemaName);
	    						schemaJson.addProperty("color", "#24A4CC");
	    						nodes.add(gson.toJson(schemaJson));
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
	    						JsonObject docTypeJson = new JsonObject();
	    						docTypeJson.addProperty("name", docTypeName);
	    						docTypeJson.addProperty("featureName", docTypeName+".doc");
	    						docTypeJson.addProperty("labelName", docTypeName);
	    						docTypeJson.addProperty("color", "#1CA5FC");
	    						nodes.add(gson.toJson(docTypeJson));
	    						transitions.add(docTypeName+"->"+docType.getExtends()+"[label=\"inherits\"]");
	    						
	    						List<Doctype.Schema> extraSchemas = docType.getSchema();
	    						for(Doctype.Schema extraSchema: extraSchemas){
	    							//Don't include common schemas for the sake of visibility
	    							if(!COMMON_SCHEMAS.contains(extraSchema.getName())){
	    								transitions.add(docTypeName+"->"+extraSchema.getName()+"_sh");
	    								
	    								if(!schemasList.contains(extraSchema.getName()+"_sh")){
		    								JsonObject schemaJson = new JsonObject();
		    								schemaJson.addProperty("name", extraSchema.getName()+"_sh");
		    								schemaJson.addProperty("featureName", extraSchema.getName()+".ds");
		    								schemaJson.addProperty("labelName", extraSchema.getName());
		    								schemaJson.addProperty("color", "#24A4CC");
		    	    						nodes.add(gson.toJson(schemaJson));
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
	    							transitions.add(docTypeName+"->"+extraFacet.getName()+"_facet");
	    							
	    							if(!facets.contains(extraFacet.getName()+"_facet")){
		    							JsonObject facetJson = new JsonObject();
		    							facetJson.addProperty("name", extraFacet.getName()+ "_facet");
		    							facetJson.addProperty("labelName", extraFacet.getName());
		    							facetJson.addProperty("color", "#17384E");
	    	    						nodes.add(gson.toJson(facetJson));
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
	    							JsonObject extraDocTypeJson = new JsonObject();
	    							extraDocTypeJson.addProperty("name", docType.getExtends());
	    							extraDocTypeJson.addProperty("featureName", docType.getExtends()+".doc");
	    							extraDocTypeJson.addProperty("labelName", docType.getExtends());
	    							extraDocTypeJson.addProperty("color", "#1CA5FC");
    	    						nodes.add(gson.toJson(extraDocTypeJson));
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

		schemas += (nbSchemas>1?" [style=invis]":"");
		docTypes += (nbDocTypes>1?" [style=invis]":"");
		facets += (nbFacets>1?" [style=invis]":"");
    	
    	data.put("nodes", nodes);
    	data.put("transitions", transitions);
    	if(nbSchemas>0) data.put("schemas", schemas);
    	if(nbDocTypes>0) data.put("docTypes", docTypes);
    	if(nbFacets>0) data.put("facets", facets);

        // File output
        Writer file = new FileWriter (new File(studiovizFolderPath+File.separator+File.separator+"inputModel.dot"));
        template.process(data, file);
        file.flush();
        file.close();

	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputModel.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", studiovizFolderPath+File.separator+"imgModel.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    
	    JsonObject json = new JsonObject();
	    byte[] bytesEncoded;		
		bytesEncoded = Base64.encodeBase64(FileUtils.readFileToByteArray(new File(studiovizFolderPath+File.separator+"imgModel.png")));			
		json.addProperty("img", "data:image/png;base64,"+new String(bytesEncoded));
		
	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", studiovizFolderPath+File.separator+"imgModel.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(studiovizFolderPath+File.separator+"imgModel.cmapx"));
	    json.addProperty("map", URLEncoder.encode(map,"UTF-8"));
	    return json;
	}

	public JsonObject generateViewGraphFromXML(String studioProjectName, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		//Freemarker configuration object
        Configuration cfg = new Configuration(new Version(2,3,0));
        
        //Load template
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        Template template = cfg.getTemplate("inputView.ftl");       
        
        // Build the data-model
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("studioProjectName", studioProjectName);
		
        ArrayList<String> nodes = new ArrayList<String>();
        ArrayList<String> transitions = new ArrayList<String>();
        
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        
		String tabs = "";
		String docTypes = "";
		String contentViews = "";
		String formLayouts = "";
		
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
		    					    				transitions.add(cleanedActionId+"_tab -> "+docTypeName);
		    					    				if(!docTypesList.contains(docTypeName)){
		    					    					JsonObject docTypeJson = new JsonObject();
		    					    					docTypeJson.addProperty("name", docTypeName);
		    					    					docTypeJson.addProperty("featureName", docTypeName+".doc");
		    					    					docTypeJson.addProperty("labelName", docTypeName);
		    					    					docTypeJson.addProperty("color", "#1CA5FC");
		    				    						nodes.add(gson.toJson(docTypeJson));
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
		    					JsonObject actionJson = new JsonObject();
		    					actionJson.addProperty("name", cleanedActionId+"_tab");
		    					actionJson.addProperty("featureName", action.getId()+".tab");
		    					actionJson.addProperty("labelName", action.getId());
		    					actionJson.addProperty("color", "#2B333E");
	    						nodes.add(gson.toJson(actionJson));
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
	    			    					JsonObject typeJson = new JsonObject();
	    			    					typeJson.addProperty("name", typeId);
	    			    					typeJson.addProperty("featureName", typeId+".doc");
	    			    					typeJson.addProperty("labelName", typeId);
	    			    					typeJson.addProperty("color", "#1CA5FC");
	    		    						nodes.add(gson.toJson(typeJson));
	    			    					if(nbDocTypes >0){
	    			    						docTypes += "->";
	    			    					}
	    									docTypes += typeId;
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}

	    								String contentViewName = contentView.getValue();
	    								String cleanedContentViewName = cleanUpForDot(contentView.getValue());
	    								transitions.add(typeId+"->"+cleanedContentViewName);
	    								
	    								if(!contentViews.contains(cleanedContentViewName)){
	    									JsonObject contentViewJson = new JsonObject();
	    									contentViewJson.addProperty("name", cleanedContentViewName);
	    									contentViewJson.addProperty("featureName", contentViewName+".contentView");
	    									contentViewJson.addProperty("labelName", contentViewName);
	    									contentViewJson.addProperty("color", "#31A3C5");
	    		    						nodes.add(gson.toJson(contentViewJson));
	    									if(nbContentViews >0){
		    		    						contentViews += "->";
		    		    					}
		    		    					contentViews += cleanedContentViewName;
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
	    							String cleanedFormLayoutName = cleanUpForDot(layout.getValue().split("@")[0]);
	    							cleanedFormLayoutName = cleanedFormLayoutName+"_fl";
	    							if(!formLayouts.contains(cleanedFormLayoutName)){
	    								
	    								if(!docTypesList.contains(typeId)){
	    			    					JsonObject contentViewJson = new JsonObject();
	    									contentViewJson.addProperty("name", typeId);
	    									contentViewJson.addProperty("featureName", typeId+".doc");
	    									contentViewJson.addProperty("labelName", typeId);
	    									contentViewJson.addProperty("color", "#1CA5FC");
	    		    						nodes.add(gson.toJson(contentViewJson));
	    			    					if(nbDocTypes >0){
	    			    						docTypes += "->";
	    			    					}
	    									docTypes += typeId;
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}
	    								
	    								transitions.add(typeId+"->"+cleanedFormLayoutName);
    									JsonObject formLayoutJson = new JsonObject();
    									formLayoutJson.addProperty("name", cleanedFormLayoutName);
    									formLayoutJson.addProperty("featureName", formLayoutName+".layout");
    									formLayoutJson.addProperty("labelName", formLayoutName);
    									formLayoutJson.addProperty("color", "#FC4835");
    		    						nodes.add(gson.toJson(formLayoutJson));
    									if(nbFormLayouts >0){
	    		    						formLayouts += "->";
	    		    					}
	    		    					formLayouts += cleanedFormLayoutName;
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

		tabs += (nbTabs>1?" [style=invis]":"");
		docTypes += (nbDocTypes>1?" [style=invis]":"");
		contentViews +=  (nbContentViews>1?" [style=invis]":"");
		formLayouts +=  (nbFormLayouts>1?" [style=invis]":"");

		data.put("nodes", nodes);
    	data.put("transitions", transitions);
    	if(nbTabs>0) data.put("tabs", tabs);
    	if(nbDocTypes>0) data.put("docTypes", docTypes);
    	if(nbContentViews>0) data.put("contentViews", contentViews);
    	if(nbFormLayouts>0) data.put("formLayouts", formLayouts);

        // File output
        Writer file = new FileWriter (new File(studiovizFolderPath+File.separator+File.separator+"inputView.dot"));
        template.process(data, file);
        file.flush();
        file.close();
		
	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputView.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", studiovizFolderPath+File.separator+"imgView.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    
	    JsonObject json = new JsonObject();
	    byte[] bytesEncoded;
	    bytesEncoded = Base64.encodeBase64(FileUtils.readFileToByteArray(new File(studiovizFolderPath+File.separator+"imgView.png")));			
		json.addProperty("img", "data:image/png;base64,"+new String(bytesEncoded));

	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", studiovizFolderPath+File.separator+"imgView.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(studiovizFolderPath+File.separator+"imgView.cmapx"));
	    json.addProperty("map", URLEncoder.encode(map,"UTF-8"));
	    return json;
	}

	public JsonObject generateBusinessRulesGraphFromXML(String studioProjectName, String studiovizFolderPath, CommandLineExecutorComponent commandLineExecutorComponent, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException{
		JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		String map = "";
		Component component = (Component) unmarshaller.unmarshal(new File(studiovizFolderPath+File.separator+"OSGI-INF"+File.separator+"extensions.xml"));

		//Freemarker configuration object
        Configuration cfg = new Configuration(new Version(2,3,0));
        
        //Load template
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        Template template = cfg.getTemplate("inputBusinessRules.ftl");
        
        // Build the data-model
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("studioProjectName", studioProjectName);
        
        ArrayList<String> nodes = new ArrayList<String>();
        ArrayList<String> transitions = new ArrayList<String>();
        
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        
		String userActions = "";
		String automationChainsAndScripting = "";
		String events =  "";	
		String wfTasks =  "";
		
		List<Extension> extensions = component.getExtension();
		String pattern = "\\#\\{operationActionBean.doOperation\\('(.*)'\\)\\}";
		// Create a Pattern object
		Pattern r = Pattern.compile(pattern);
		int nbUserActions = 0;
		int nbAutomationChains = 0;
		int nbAutomationScripting = 0;
		int nbEvents = 0;
		int nbWfTasks = 0;
		
		for(Extension extension:extensions){
			String point = extension.getPoint();
		    switch (point){
		    	case EXTENSIONPOINT_ACTIONS :
		    		try{
		    			List<Action> actions = extension.getAction();
		    			for(Action action:actions){
		    				String chainId = "";		    				
		    				try{
		    					chainId = action.getLink();
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
		    					
		    					//contextual graph
		    					//skip this one if it's not in the list of Chains to display		 
		    					if(nodeList != null && !nodeList.contains(cleanedChainId)){
		    						continue;
		    					}
		    								    					
		    					String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";
		    					transitions.add(cleanedActionId+"_action -> "+cleanedChainId);
			    				
		    					if(!automationList.contains(cleanedChainId)){
			    					JsonObject chainJson = new JsonObject();
			    					chainJson.addProperty("name", cleanedChainId);
			    					chainJson.addProperty("featureName", refChainId);
			    					chainJson.addProperty("labelName", chainId);
			    					chainJson.addProperty("color", "#28A3C7");
		    						nodes.add(gson.toJson(chainJson));
			    					
			    					if(nbAutomationChains >0 || nbAutomationScripting >0){
			    						automationChainsAndScripting += "->";
				    				}

			    					automationChainsAndScripting += cleanedChainId;
			    					automationList.add(cleanedChainId);
			    					if(chainId.startsWith("javascript")){
				    					nbAutomationScripting ++;
				    				}else{
				    					nbAutomationChains ++;
				    				}			    					
			    				}
			    				JsonObject actionJson = new JsonObject();
			    				actionJson.addProperty("name", cleanedActionId+"_action");
			    				actionJson.addProperty("featureName", action.getId()+".action");
			    				actionJson.addProperty("labelName", action.getId()+"\n"+(action.getLabel()!= null ? action.getLabel():""));
			    				actionJson.addProperty("color", "#00ADFF");
	    						nodes.add(gson.toJson(actionJson));
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
		    						    				
		    				//contextual graph
	    					//skip this one if it's not in the list of Chains to display
		    				boolean mainChainIsPartOfTheNodeList = true;
		    				boolean secondChainIsPartOfTheNodeList = false;
	    					if(nodeList != null && !nodeList.contains(chainIdForDot)){
	    						mainChainIsPartOfTheNodeList = false;
	    					}
		    				
		    				//handle the link between 2 Automation chains
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
	    						    						    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(1))) || mainChainIsPartOfTheNodeList){
	    						    						String cleanedSecondChain = cleanUpForDot(m.group(1));
	    						    						transitions.add(chainIdForDot+" -> "+cleanedSecondChain);
	    						    						secondChainIsPartOfTheNodeList = true;
	    						    						if(!automationList.contains(cleanedSecondChain)){
				    											if(nbAutomationChains >0 || nbAutomationScripting >0){
				    					    						automationChainsAndScripting += "->";
				    						    				}
				    					    					automationChainsAndScripting += cleanedSecondChain;
				    					    					automationList.add(cleanedSecondChain);
				    					    					if(chainId.startsWith("javascript")){
				    						    					nbAutomationScripting ++;
				    						    				}else{
				    						    					nbAutomationChains ++;
				    						    				}	
				    					    					String refSecondChainId = m.group(1).startsWith("javascript.")? m.group(1).replace("javascript.", "")+".scriptedOperation" : m.group(1)+".ops";
										    					JsonObject secondChainJson = new JsonObject();
										    					secondChainJson.addProperty("name", cleanedSecondChain);
										    					secondChainJson.addProperty("featureName", refSecondChainId);
										    					secondChainJson.addProperty("labelName", m.group(1));
										    					secondChainJson.addProperty("color", "#28A3C7");
									    						nodes.add(gson.toJson(secondChainJson));
			    											}	    
	    						    						
	    						    					}
	    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(2))) || mainChainIsPartOfTheNodeList){	    						    			
	    						    						String cleanedSecondChain = cleanUpForDot(m.group(2));
	    						    						transitions.add(chainIdForDot+" -> "+cleanedSecondChain);
	    						    						
	    						    						secondChainIsPartOfTheNodeList = true;
	    						    						if(!automationList.contains(cleanedSecondChain)){
				    											if(nbAutomationChains >0 || nbAutomationScripting >0){
				    					    						automationChainsAndScripting += "->";
				    						    				}
				    					    					automationChainsAndScripting += cleanedSecondChain;
				    					    					automationList.add(cleanedSecondChain);
				    					    					if(chainId.startsWith("javascript")){
				    						    					nbAutomationScripting ++;
				    						    				}else{
				    						    					nbAutomationChains ++;
				    						    				}
				    					    					String refSecondChainId = m.group(2).startsWith("javascript.")? m.group(2).replace("javascript.", "")+".scriptedOperation" : m.group(2)+".ops";
										    					JsonObject secondChainJson = new JsonObject();
										    					secondChainJson.addProperty("name", cleanedSecondChain);
										    					secondChainJson.addProperty("featureName", refSecondChainId);
										    					secondChainJson.addProperty("labelName", m.group(2));
										    					secondChainJson.addProperty("color", "#28A3C7");
									    						nodes.add(gson.toJson(secondChainJson));
			    											}	
	    						    					}
	    						    					
	    						    				}
	    										}else{
	    											if(nodeList == null || (nodeList != null && nodeList.contains(cleanUpForDot(param.getValue()))) || mainChainIsPartOfTheNodeList){
	    												String cleanedSecondChain = cleanUpForDot(param.getValue());
	    												secondChainIsPartOfTheNodeList = true;
	    												transitions.add(chainIdForDot+" -> "+cleanedSecondChain);
		    											if(!automationList.contains(cleanedSecondChain)){
			    											if(nbAutomationChains >0 || nbAutomationScripting >0){
			    					    						automationChainsAndScripting += "->";
			    						    				}

			    					    					automationChainsAndScripting += cleanedSecondChain;
			    					    					automationList.add(cleanedSecondChain);
			    					    					if(chainId.startsWith("javascript")){
			    						    					nbAutomationScripting ++;
			    						    				}else{
			    						    					nbAutomationChains ++;
			    						    				}
			    					    					String refSecondChainId = param.getValue().startsWith("javascript.")? param.getValue().replace("javascript.", "")+".scriptedOperation" : param.getValue()+".ops";
									    					JsonObject secondChainJson = new JsonObject();
									    					secondChainJson.addProperty("name", cleanedSecondChain);
									    					secondChainJson.addProperty("featureName", refSecondChainId);
									    					secondChainJson.addProperty("labelName", param.getValue());
									    					secondChainJson.addProperty("color", "#28A3C7");
								    						nodes.add(gson.toJson(secondChainJson));
		    											}	    												
	    											}
	    										}
	    									}
	    								}
	    							//handle the link between an Automation chain & scripting
	    							}else if(operation.getId().startsWith("javascript.")){
	    								if(nodeList == null || (nodeList != null && nodeList.contains(cleanUpForDot(operation.getId()))) || mainChainIsPartOfTheNodeList){
	    									String cleanedSecondChain = cleanUpForDot(operation.getId());
	    									secondChainIsPartOfTheNodeList = true;
	    									transitions.add(chainIdForDot+" -> "+cleanedSecondChain);
											if(!automationList.contains(cleanedSecondChain)){
												if(nbAutomationChains >0 || nbAutomationScripting >0){
						    						automationChainsAndScripting += "->";
							    				}
						    					automationChainsAndScripting += cleanedSecondChain;
						    					automationList.add(cleanedSecondChain);
							    				nbAutomationScripting ++;	
							    				
							    				String refSecondChainId = operation.getId().startsWith("javascript.")? operation.getId().replace("javascript.", "")+".scriptedOperation" : operation.getId()+".ops";
						    					JsonObject secondChainJson = new JsonObject();
						    					secondChainJson.addProperty("name", cleanedSecondChain);
						    					secondChainJson.addProperty("featureName", refSecondChainId);
						    					secondChainJson.addProperty("labelName", operation.getId());
						    					secondChainJson.addProperty("color", "#28A3C7");
					    						nodes.add(gson.toJson(secondChainJson));
											}
	    								}
	    							}
	    						}
	    					}
	    					
	    					if(!mainChainIsPartOfTheNodeList && !secondChainIsPartOfTheNodeList){
	    						continue;
	    					}
	    					
	    					if(!automationList.contains(chainIdForDot)){
		    					if(nbAutomationChains >0 || nbAutomationScripting >0){
		    						automationChainsAndScripting += "->";
			    				}
	
		    					automationChainsAndScripting += chainIdForDot;
		    					automationList.add(chainIdForDot);
		    					if(chainId.startsWith("javascript")){
			    					nbAutomationScripting ++;
			    				}else{
			    					nbAutomationChains ++;
			    				}
		    					
		    					String description = (chain.getDescription() != null ? "\n"+chain.getDescription():"");
		    					JsonObject chainJson = new JsonObject();
		    					chainJson.addProperty("name", chainIdForDot);
		    					chainJson.addProperty("featureName", refChainId);
		    					chainJson.addProperty("labelName", chainId+description);
		    					chainJson.addProperty("color", "#28A3C7");
	    						nodes.add(gson.toJson(chainJson));
	    						
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
	    					
	    					if(nodeList != null && !nodeList.contains(chainIdForDot)){
	    						continue;
	    					}

	    					JsonObject eventJson = new JsonObject();
	    					eventJson.addProperty("name", chainIdForDot+"_handler");
	    					eventJson.addProperty("labelName", handler.getChainId()+"_handler");
	    					eventJson.addProperty("color", "#FF462A");
    						nodes.add(gson.toJson(eventJson));
    						transitions.add(chainIdForDot+"_handler"+" -> "+chainIdForDot);

	    					if(nbEvents > 0){
	    						events += "->";
	    					}
	    					events += cleanUpForDot(handler.getChainId())+"_handler";
	    					nbEvents ++;

	    					if(!automationList.contains(chainIdForDot)){
		    					if(nbAutomationChains >0 || nbAutomationScripting >0){
		    						automationChainsAndScripting += "->";
			    				}

		    					automationChainsAndScripting += chainIdForDot;
		    					automationList.add(chainIdForDot);
		    					if(chainIdForDot.startsWith("javascript")){
			    					nbAutomationScripting ++;
			    				}else{
			    					nbAutomationChains ++;
			    				}
		    					JsonObject chainJson = new JsonObject();
		    					chainJson.addProperty("name", chainIdForDot);
		    					chainJson.addProperty("labelName", handler.getChainId());
		    					chainJson.addProperty("color", "#28A3C7");
	    						nodes.add(gson.toJson(chainJson));
		    				}


	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting Event Handlers", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_ROUTE_MODEL_IMPORTER :
		    		List<TemplateResource> trList = extension.getTemplateResource();
		    		for(TemplateResource tr : trList){
		    			Runtime rt = Runtime.getRuntime();
		    		    String[] cmd = { "/bin/sh", "-c", "cd "+studiovizFolderPath+File.separator+"data; jar xf "+tr.getPath().replace("data/", "") };    
		    		    Process p = rt.exec(cmd);
		    		    try {
		    				p.waitFor();
		    			} catch (InterruptedException e) {
		    				logger.error("Error while unzipping ["+tr.getPath()+"]");
		    			}			
		    		    
		    		    //Get all the tasks under the Workflow folder
		    		    File file = new File(studiovizFolderPath+File.separator+"data"+File.separator+tr.getId());
		    		    
		    			String[] tasks = file.list(new FilenameFilter() {
		    			  @Override
		    			  public boolean accept(File current, String name) {
		    			    return new File(current, name).isDirectory();	    
		    			  }
		    			});
		    			
		    			if(tasks != null){
			    			for(String task: tasks){			    				
			    				//Use task as the id of the node			    				
				    			//Read the content of the document.xml
				    			String xmlText = FileUtils.readFileToString(new File(studiovizFolderPath+File.separator+"data"+File.separator+tr.getId()+File.separator+task+File.separator+"document.xml"));
				    			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				    			factory.setNamespaceAware(true);
				    			DocumentBuilder db;
								try {
									db = factory.newDocumentBuilder();
									InputStream in = new ByteArrayInputStream(xmlText.getBytes("UTF-8"));
					    			Document doc = db.parse(in);
					    			
					    			//title
					    			String title = "";
					    			NodeList nodeTitle = doc.getElementsByTagName("dc:title");
					    			if(nodeTitle != null && nodeTitle.getLength()>0){
					    				title = nodeTitle.item(0).getTextContent();
					    			}
					    			
					    			//description
					    			String desc = "";
					    			NodeList nodeDesc = doc.getElementsByTagName("dc:description");
					    			if(nodeDesc != null && nodeDesc.getLength()>0){
					    				desc = nodeDesc.item(0).getTextContent();
					    			}
					    			
					    			//inputChain
					    			String inputChain = "";
					    			NodeList nodeIC = doc.getElementsByTagName("rnode:inputChain");
					    			if(nodeIC != null && nodeIC.getLength()>0){
					    				inputChain = cleanUpForDot(nodeIC.item(0).getTextContent());
					    				
					    			}
					    			
					    			//outputChain
					    			String outputChain = "";
					    			NodeList nodeOC = doc.getElementsByTagName("rnode:outputChain");
					    			if(nodeOC != null && nodeOC.getLength()>0){
					    				outputChain = cleanUpForDot(nodeOC.item(0).getTextContent());
					    			}
					    			
					    			if(nodeList != null && !nodeList.contains(inputChain) && !nodeList.contains(outputChain)){
			    						continue;
			    					}
					    			
					    			if(!("").equals(inputChain) || !("").equals(outputChain)){
					    				if(nodeList == null || (nodeList != null && nodeList.contains(inputChain))){
						    				if(!("").equals(inputChain)){
						    					if(!automationList.contains(inputChain)){
							    					if(nbAutomationChains >0 || nbAutomationScripting >0){
							    						automationChainsAndScripting += "->";
								    				}
							    					if(inputChain.startsWith("javascript")){
								    					nbAutomationScripting ++;
								    				}else{
								    					nbAutomationChains ++;
								    				}
							    					automationChainsAndScripting += inputChain;
							    					automationList.add(inputChain);
						    					
							    					String refChainId = inputChain.startsWith("javascript.")? inputChain.replace("javascript.", "")+".scriptedOperation" : inputChain+".ops";
							    					JsonObject inputChainJson = new JsonObject();
							    					inputChainJson.addProperty("name", refChainId);
							    					inputChainJson.addProperty("featureName", refChainId);
							    					inputChainJson.addProperty("labelName", refChainId);
							    					inputChainJson.addProperty("color", "#28A3C7");
						    						nodes.add(gson.toJson(inputChainJson));
						    					}
						    					transitions.add(task + " -> "+ inputChain);
						    				}
					    				}
					    				if(nodeList == null || (nodeList != null && nodeList.contains(outputChain))){
						    				if(!("").equals(outputChain)){
						    					if(!automationList.contains(outputChain)){
							    					if(nbAutomationChains >0 || nbAutomationScripting >0){
							    						automationChainsAndScripting += "->";
								    				}
							    					if(outputChain.startsWith("javascript")){
								    					nbAutomationScripting ++;
								    				}else{
								    					nbAutomationChains ++;
								    				}
							    					automationChainsAndScripting += outputChain;
							    					automationList.add(outputChain);
						    					
							    					String refChainId = outputChain.startsWith("javascript.")? outputChain.replace("javascript.", "")+".scriptedOperation" : outputChain+".ops";
							    					JsonObject outputChainJson = new JsonObject();
							    					outputChainJson.addProperty("name", refChainId);
							    					outputChainJson.addProperty("featureName", refChainId);
							    					outputChainJson.addProperty("labelName", outputChain);
							    					outputChainJson.addProperty("color", "#28A3C7");
						    						nodes.add(gson.toJson(outputChainJson));
						    					}
						    					transitions.add(task + " -> "+ outputChain);
						    				}
					    				}
					    				
					    				if(nodeList != null && nodeList.isEmpty()){
					    					continue;
					    				}
					    				
					    				if(nodeList == null || (nodeList != null && nodeList.contains(outputChain)) || (nodeList != null && nodeList.contains(inputChain))){
						    				if(!wfTasks.contains(task)){
						    					String taskName = tr.getId()+"\n"+title+ (!desc.equals("")? "\n"+desc :"");
						    					JsonObject taskJson = new JsonObject();
						    					taskJson.addProperty("name", task);
						    					taskJson.addProperty("featureName", tr.getId()+".workflow");
						    					taskJson.addProperty("labelName", taskName);
						    					taskJson.addProperty("color", "#1BB249");
					    						nodes.add(gson.toJson(taskJson));
						    					if(nbWfTasks>0){
						    						wfTasks += "->"; 
						    					}
						    					wfTasks += task;
						    					nbWfTasks ++;
						    				}
					    				}
					    				
					    			}					    			
								} catch (ParserConfigurationException e) {
									logger.error("Error while getting Worflow Tasks",e);
								} catch (SAXException e) {
									logger.error("Error while getting Worflow Tasks",e);
								}
			    			}
		    			}			    						    			
		    		}
		    		break;
	    	}
	    }

		userActions += (nbUserActions>1?" [style=invis]":"");
		automationChainsAndScripting += (nbAutomationChains>1?" [style=invis]":"");
		events += (nbEvents>1?" [style=invis]":"");
		wfTasks += (nbWfTasks>1?" [style=invis]":"");
		
		data.put("nodes", nodes);
    	data.put("transitions", transitions);
    	if(nbUserActions>0) data.put("userActions", userActions);
    	if(nbAutomationChains>0) data.put("automationChainsAndScripting", automationChainsAndScripting);
    	if(nbEvents>0) data.put("events", events);
    	if(nbWfTasks>0) data.put("wfTasks", wfTasks);

        // File output
        Writer file = new FileWriter (new File(studiovizFolderPath+File.separator+File.separator+"inputBusinessRules.dot"));
        template.process(data, file);
        file.flush();
        file.close();
        
	    CmdParameters parameters = new CmdParameters();

	    //Generate png from dot
	    parameters.addNamedParameter("inputFile", studiovizFolderPath+File.separator+"inputBusinessRules.dot");
	    parameters.addNamedParameter("format", "png");
	    parameters.addNamedParameter("outputFile", studiovizFolderPath+File.separator+"imgBusinessRules.png");
	    commandLineExecutorComponent.execCommand("dot", parameters);

	    JsonObject json = new JsonObject();
	    byte[] bytesEncoded;
		bytesEncoded = Base64.encodeBase64(FileUtils.readFileToByteArray(new File(studiovizFolderPath+File.separator+"imgBusinessRules.png")));			
		json.addProperty("img", "data:image/png;base64,"+new String(bytesEncoded));
			    
	    //Generate map from dot
	    parameters.addNamedParameter("format", "cmapx");
	    parameters.addNamedParameter("outputFile", studiovizFolderPath+File.separator+"imgBusinessRules.cmapx");
	    commandLineExecutorComponent.execCommand("dot", parameters);
	    map = FileUtils.readFileToString(new File(studiovizFolderPath+File.separator+"imgBusinessRules.cmapx"));
	    json.addProperty("map", URLEncoder.encode(map,"UTF-8"));
	    return json;
	}
}
