/**
 * 
 */

package org.nuxeo.studioviz.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.jaxb.Component;
import org.nuxeo.jaxb.Component.Extension;
import org.nuxeo.jaxb.Component.Extension.Action;
import org.nuxeo.jaxb.Component.Extension.Action.Filter;
import org.nuxeo.jaxb.Component.Extension.Action.Filter.Rule;
import org.nuxeo.jaxb.Component.Extension.Action.Filter.Rule.Type;
import org.nuxeo.jaxb.Component.Extension.Chain;
import org.nuxeo.jaxb.Component.Extension.Doctype;
import org.nuxeo.jaxb.Component.Extension.Schema;
import org.nuxeo.jaxb.Component.Extension.TemplateResource;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews;
import org.nuxeo.jaxb.Component.Extension.Type.ContentViews.ContentView;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts;
import org.nuxeo.jaxb.Component.Extension.Type.Layouts.Layout;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.studioviz.helper.GraphHelper;
import org.osgi.framework.Bundle;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import freemarker.template.Version;


/**
 * @author mgena
 */
public class StudioVizComponent extends DefaultComponent implements StudioVizService {

	private Log logger = LogFactory.getLog(StudioVizComponent.class);
    protected Bundle bundle;
    public static final String STUDIOVIZ_FOLDER = "studioviz";
    private static final String EXTENSIONPOINT_SCHEMAS = "schema";
    private static final String EXTENSIONPOINT_DOCTYPE = "doctype";
    private static final String COMMON_SCHEMAS = "common,dublincore,uid,task,file,picture,image_metadata,iptc,publishing,webcontainer,files";
    private static final String EXTENSIONPOINT_TYPES = "types";
    private static final String EXTENSIONPOINT_CHAIN = "chains";
    private static final String EXTENSIONPOINT_ACTIONS = "actions";
    private static final String EXTENSIONPOINT_ROUTE_MODEL_IMPORTER = "routeModelImporter";
 
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
    
    private Component getComponent(String studioJarPath) throws JAXBException, IOException{
    	JAXBContext jc = JAXBContext.newInstance("org.nuxeo.jaxb");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		
		Component component = null;
		JarFile file = new JarFile(studioJarPath);
        Enumeration<JarEntry> entries = file.entries();
        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().equals("OSGI-INF/extensions.xml")) {
                try(InputStream is = file.getInputStream(entry)) {
                	component = (Component) unmarshaller.unmarshal(is);
                }
                break;
            }
        }
	    
        if (component == null) {
        	// log pqs d extensions.xml
        	return null;
        }
        return component;
    }
    
    private Template initializeFreemarker(String ftl) throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException{
    	//Freemarker configuration object
        Configuration cfg = new Configuration(new Version(2,3,0));
        
        //Load template
        cfg.setClassForTemplateLoading(this.getClass(), "/templates");
        Template template = cfg.getTemplate(ftl);
        return template;
    }
    
    private JsonObject buildGraphResultAsJson(Template template,Map<String, Object> data, String input, String img, String cmapx) throws IOException, CommandNotAvailable, TemplateException{
    	//Create temporary directory
    	String tmpDir = Environment.getDefault().getTemp().getPath();
    	Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
    	Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, "studioviz")
    	                   : Framework.createTempDirectory(null);

        // File output
    	Path inputFilePath = Paths.get(outDirPath.toString(), input);
    	try(Writer dotFile = new FileWriter (inputFilePath.toFile())){
    		template.process(data, dotFile);
    	}
    	
    	Blob blob = new FileBlob(new File(inputFilePath.toString()));
    	
    	GraphVizService gvs = Framework.getService(GraphVizService.class);
        
    	Blob imgFileBlob = gvs.generate(blob, input, img, "png");
    	Blob cmapFileBlob = gvs.generate(blob, input, cmapx, "cmapx");
	    JsonObject json = new JsonObject();
	    byte[] bytesEncoded;
	   
		bytesEncoded = Base64.encodeBase64(imgFileBlob.getByteArray());	
		
		//build the img path
	    CodeSource src = Framework.class.getProtectionDomain().getCodeSource();
	    String imgPath = null;
	    if (src != null) {
	    	imgPath = src.getLocation().toString();
	    	String path[] = imgPath.split(File.separator);
	    	imgPath = imgPath.replace(path[path.length-2]+File.separator, "");
	    	imgPath = imgPath.replace(File.separator+path[path.length-1], "");
	    	imgPath = imgPath+File.separator+"nuxeo.war"+File.separator+"studioviz"+File.separator+img;
	    	imgPath = imgPath.replace("file:","");
	    }
	    imgFileBlob.transferTo(new File(imgPath));
	    json.addProperty("img", "/nuxeo/studioviz/"+img);
		//json.addProperty("img", "data:image/png;base64,"+new String(bytesEncoded));
		
	    String map = cmapFileBlob.getString();
	    json.addProperty("map", URLEncoder.encode(map,"UTF-8"));
	    
	    //Delete temporary directory
	    
	    GraphHelper.deleteFolder(imgFileBlob.getFile().getParentFile());
	    
	    return json;
    }

	public JsonObject generateModelGraphFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException{
		Component component = getComponent(studioJarPath);   
        String studioProjectName = FilenameUtils.getBaseName(studioJarPath).replace("-"+GraphHelper.SNAPSHOT_SUFFIX, "");    	       
        Template template = initializeFreemarker("inputModel.ftl");
        
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
	    					if(docTypeName != null && !docTypeName.endsWith("_cv") && !docTypeName.equals("null")){
	    						JsonObject docTypeJson = new JsonObject();
	    						docTypeJson.addProperty("name", docTypeName);
	    						docTypeJson.addProperty("featureName", docTypeName+".doc");
	    						docTypeJson.addProperty("labelName", docTypeName);
	    						docTypeJson.addProperty("color", "#1CA5FC");
	    						nodes.add(gson.toJson(docTypeJson));
	    						if(!docType.getExtends().equals("null")){
	    							transitions.add(docTypeName+"->"+docType.getExtends()+"[label=\"inherits\"]");
	    						}
	    						
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

	    						if(!docTypesList.contains(docType.getExtends()) && !docType.getExtends().equals("null")){
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

    	JsonObject json = buildGraphResultAsJson(template, data, "inputModel.dot","imgModel.png", "imgModel.cmapx");
    	
	    return json;
	}
	
	public JsonObject generateViewGraphFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException{
		Component component = getComponent(studioJarPath);   
        String studioProjectName = FilenameUtils.getBaseName(studioJarPath).replace("-"+GraphHelper.SNAPSHOT_SUFFIX, "");    	       
        Template template = initializeFreemarker("inputView.ftl");
        
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
		    				String cleanedActionId = GraphHelper.cleanUpForDot(action.getId());

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
	    								String cleanedContentViewName = GraphHelper.cleanUpForDot(contentView.getValue());
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
	    						if(layout != null && layout.getValue() != null &&!layout.getValue().startsWith("layout@") && !typeId.endsWith("_cv")){
	    							String formLayoutName = layout.getValue().split("@")[0];
	    							String cleanedFormLayoutName = GraphHelper.cleanUpForDot(layout.getValue().split("@")[0]);
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

    	JsonObject json = buildGraphResultAsJson(template, data, "inputView.dot","imgView.png", "imgView.cmapx");
	    return json;
	}
	
	public JsonObject generateBusinessRulesGraphFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException{
		
		ArrayList<String> automationList = new ArrayList<String>();
		
		Component component = getComponent(studioJarPath);   
        String studioProjectName = FilenameUtils.getBaseName(studioJarPath).replace("-"+GraphHelper.SNAPSHOT_SUFFIX, "");    	       
        Template template = initializeFreemarker("inputBusinessRules.ftl");
        
        JarFile file = new JarFile(studioJarPath);
        Enumeration<JarEntry> entries = file.entries();
        
        //Create temporary directory
    	String tmpDir = Environment.getDefault().getTemp().getPath();
    	Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
    	Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, "studioviz")
    	                   : Framework.createTempDirectory(null);

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
		    				String cleanedActionId = GraphHelper.cleanUpForDot(action.getId());

		    				if(chainId != null && !("").equals(chainId) && !(".").equals(chainId)  && !chainId.endsWith("xhtml")){
		    					String cleanedChainId = GraphHelper.cleanUpForDot(chainId);
		    					
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

		    				String chainIdForDot = GraphHelper.cleanUpForDot(chain.getId());
		    						    				
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
	    											String exprPattern = "\"(.*)\":\"(.*)\"";
	    											Pattern expR = Pattern.compile(exprPattern);
	    											Matcher m = expR.matcher(param.getValue().trim().replace(" ", ""));
	    						    				if (m.find( )) {
	    						    						    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(1))) || mainChainIsPartOfTheNodeList){
	    						    						String cleanedSecondChain = GraphHelper.cleanUpForDot(m.group(1));
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
	    						    						String cleanedSecondChain = GraphHelper.cleanUpForDot(m.group(2));
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
	    											if(nodeList == null || (nodeList != null && nodeList.contains(GraphHelper.cleanUpForDot(param.getValue()))) || mainChainIsPartOfTheNodeList){
	    												String cleanedSecondChain = GraphHelper.cleanUpForDot(param.getValue());
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
	    								if(nodeList == null || (nodeList != null && nodeList.contains(GraphHelper.cleanUpForDot(operation.getId()))) || mainChainIsPartOfTheNodeList){
	    									String cleanedSecondChain = GraphHelper.cleanUpForDot(operation.getId());
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
	    		case EXTENSIONPOINT_ROUTE_MODEL_IMPORTER :
		    		List<TemplateResource> trList = extension.getTemplateResource();
		    		for(TemplateResource tr : trList){
		    			
		    			entries = file.entries();
		    	        while(entries.hasMoreElements()) {
		    	            JarEntry entry = entries.nextElement();
		    	            if (entry.getName().equals(tr.getPath())) {
		    	                try(ZipInputStream zipIn = new ZipInputStream(file.getInputStream(entry))) {
		    	                	ZipEntry zipEntry = zipIn.getNextEntry();
		    	                	while (zipEntry != null) {
		    	                        String filePath = outDirPath.toString() + File.separator + zipEntry.getName();
		    	                        if (!zipEntry.isDirectory()) {
		    	                            // if the entry is a file, extracts it
		    	                            GraphHelper.extractFile(zipIn, filePath);
		    	                        } else {
		    	                            // if the entry is a directory, make the directory
		    	                            File dir = new File(filePath);
		    	                            dir.mkdir();
		    	                        }
		    	                        zipIn.closeEntry();
		    	                        zipEntry = zipIn.getNextEntry();
		    	                    }
		    	                    zipIn.close();
		    	                }
		    	                break;
		    	            }
		    	        }
		    			  
		    		    //Get all the tasks under the Workflow folder
		    		    File tasksFolder = new File(outDirPath.toString()+File.separator+tr.getId());
		    		    
		    			String[] tasks = tasksFolder.list(new FilenameFilter() {
		    			  @Override
		    			  public boolean accept(File current, String name) {
		    			    return new File(current, name).isDirectory();	    
		    			  }
		    			});
		    			
		    			if(tasks != null){
			    			for(String task: tasks){			    				
			    				//Use task as the id of the node			    				
				    			//Read the content of the document.xml
				    			String xmlText = FileUtils.readFileToString(new File(outDirPath.toString()+File.separator+tr.getId()+File.separator+task+File.separator+"document.xml"));
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
					    				inputChain = GraphHelper.cleanUpForDot(nodeIC.item(0).getTextContent());
					    				
					    			}
					    			
					    			//outputChain
					    			String outputChain = "";
					    			NodeList nodeOC = doc.getElementsByTagName("rnode:outputChain");
					    			if(nodeOC != null && nodeOC.getLength()>0){
					    				outputChain = GraphHelper.cleanUpForDot(nodeOC.item(0).getTextContent());
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
		
		//Deal with the Event Handlers		
		entries = file.entries();
        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".evt.xml")) {
                try(InputStream is = file.getInputStream(entry)) {
	    			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    			factory.setNamespaceAware(true);
	    			DocumentBuilder db;
	    			db = factory.newDocumentBuilder();
	    			Document doc = db.parse(is);
	    			
	    			NodeList nodeId = doc.getElementsByTagName("events");
	    			String eventHandlerId = nodeId.item(0).getAttributes().getNamedItem("id").getNodeValue();
	    			String eventHandlerIdForDot = GraphHelper.cleanUpForDot(eventHandlerId);
	    			eventHandlerIdForDot += eventHandlerIdForDot+"_evt";
	    			
	    			NodeList nodeChainId = doc.getElementsByTagName("chain");
	    			String chainId = nodeChainId.item(0).getTextContent();
	    			String chainIdForDot = GraphHelper.cleanUpForDot(chainId);
	    			
					if(nodeList != null && !nodeList.contains(chainIdForDot)){
						continue;
					}

					JsonObject eventJson = new JsonObject();
					eventJson.addProperty("name", eventHandlerIdForDot);
					eventJson.addProperty("featureName", eventHandlerId+".evt");
					eventJson.addProperty("labelName", eventHandlerId);
					eventJson.addProperty("color", "#FF462A");
					nodes.add(gson.toJson(eventJson));
					transitions.add(eventHandlerIdForDot+" -> "+chainIdForDot);

					if(nbEvents > 0){
						events += "->";
					}
					events += eventHandlerIdForDot;
					nbEvents ++;

					//Normally we won't go through this
					//as we already parsed all the automation chains previously
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
    					String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";

    					JsonObject chainJson = new JsonObject();
    					chainJson.addProperty("name", chainIdForDot);
    					chainJson.addProperty("featureName", refChainId);
    					chainJson.addProperty("labelName", chainId);
    					chainJson.addProperty("color", "#28A3C7");
						nodes.add(gson.toJson(chainJson));
    				}	    			
                } catch (ParserConfigurationException e) {
					logger.error("Error while getting Event Handlers", e);
				} catch (SAXException e) {
					logger.error("Error while getting Event Handlers", e);
				}
            }
        }
		
		userActions += (nbUserActions>1?" [style=invis]":"");
		automationChainsAndScripting += ((nbAutomationChains+nbAutomationScripting) >1 ?" [style=invis]":"");
		events += (nbEvents>1?" [style=invis]":"");
		wfTasks += (nbWfTasks>1?" [style=invis]":"");
		
		data.put("nodes", nodes);
    	data.put("transitions", transitions);
    	if(nbUserActions>0) data.put("userActions", userActions);
    	
    	if(nbAutomationChains >0 || nbAutomationScripting >0) data.put("automationChainsAndScripting", automationChainsAndScripting);
    	if(nbEvents>0) data.put("events", events);
    	if(nbWfTasks>0) data.put("wfTasks", wfTasks);
 
    	JsonObject json = buildGraphResultAsJson(template, data, "inputBusinessRules.dot","imgBusinessRules.png", "imgBusinessRules.cmapx");
        
	    Collections.sort(automationList);
	    json.addProperty("automationList", new Gson().toJson(automationList));
	    return json;
	}

	@Override
	public Blob generateModelTextFromXML(String studioJarPath, List<String> nodeList)
			throws JAXBException, CommandNotAvailable, IOException, TemplateException {
		Component component = getComponent(studioJarPath);   
        String studioProjectName = FilenameUtils.getBaseName(studioJarPath).replace("-"+GraphHelper.SNAPSHOT_SUFFIX, "");    	       
        Template template = initializeFreemarker("inputModelText.ftl");
        
        // Build the data-model
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("studioProjectName", studioProjectName);
		
        String facets = "";

		List<Extension> extensions = component.getExtension();

		int nbSchemas = 0;
		int nbDocTypes = 0;
		int nbFacets = 0;
		ArrayList<String> docTypesList = new ArrayList<String>();
		ArrayList<String> schemasList = new ArrayList<String>();
		ArrayList<String> facetsList = new ArrayList<String>();
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
	    						
	    						List<Doctype.Schema> extraSchemas = docType.getSchema();
	    						for(Doctype.Schema extraSchema: extraSchemas){
	    							//Don't include common schemas for the sake of visibility
	    							if(!COMMON_SCHEMAS.contains(extraSchema.getName())){
	    								
	    								if(!schemasList.contains(extraSchema.getName()+"_sh")){
		    	    						schemasList.add(extraSchema.getName()+"_sh");
		    	    						nbSchemas ++;
	    								}
	    							}
	    						}

	    						List<Doctype.Facet> extraFacets = docType.getFacet();
	    						for(Doctype.Facet extraFacet : extraFacets){
	    							
	    							if(!facets.contains(extraFacet.getName()+"_facet")){
		    							
		    							facets += extraFacet.getName()+"_facet";
		    							nbFacets ++;
		    							facetsList.add(extraFacet.getName());
		    						}
	    						}
	    						
	    						nbDocTypes ++;

	    						if(!docTypesList.contains(docType.getExtends())){
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
		
		for(int i=0; i< schemasList.size(); i++){
			String schema = schemasList.get(i);
			schemasList.remove(i);
			schemasList.add(i,schema.replace("_sh", ""));
		}
		
		for(int i=0; i< facetsList.size(); i++){
			String facet = facetsList.get(i);
			facetsList.remove(i);
			facetsList.add(i,facet.replace("_facet", ""));
		}

		Collections.sort(schemasList);
		Collections.sort(docTypesList);
		Collections.sort(facetsList);
		
    	if(nbSchemas>0) data.put("schemas", schemasList);
    	if(nbDocTypes>0) data.put("docTypes", docTypesList);
    	if(nbFacets>0) data.put("facets", facetsList);

    	String tmpDir = Environment.getDefault().getTemp().getPath();
    	Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
    	Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, "studioviz")
    	                   : Framework.createTempDirectory(null);

        // File output
    	Path inputFilePath = Paths.get(outDirPath.toString(), "inputModelText.txt");
    	try(Writer txtFile = new FileWriter (inputFilePath.toFile())){
    		template.process(data, txtFile);
    	}
    	
    	Blob blob = new FileBlob(new File(inputFilePath.toString()), "text/plain");
    	
	    return blob;
	}

	@Override
	public Blob generateViewTextFromXML(String studioJarPath, List<String> nodeList)
			throws JAXBException, CommandNotAvailable, IOException, TemplateException {
		Component component = getComponent(studioJarPath);   
        String studioProjectName = FilenameUtils.getBaseName(studioJarPath).replace("-"+GraphHelper.SNAPSHOT_SUFFIX, "");    	       
        Template template = initializeFreemarker("inputViewText.ftl");
        
        // Build the data-model
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("studioProjectName", studioProjectName);
		
       
		String contentViews = "";
		String formLayouts = "";
		
		List<Extension> extensions = component.getExtension();

		int nbTabs = 0;
		int nbDocTypes = 0;
		int nbContentViews = 0;
		int nbFormLayouts = 0;
		ArrayList<String> docTypesList = new ArrayList<String>();
		ArrayList<String> tabsList = new ArrayList<String>();
		ArrayList<String> contentsList = new ArrayList<String>();
		ArrayList<String> formLayoutsList = new ArrayList<String>();
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
		    				String cleanedActionId = GraphHelper.cleanUpForDot(action.getId());

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
		    					    				if(!docTypesList.contains(docTypeName)){
		    		    								docTypesList.add(docTypeName);
		    		    								nbDocTypes ++;
		    					    				}

		    									}
		    								}
		    							}
		    						}
		    					}
		    				}
		    				if(!tabsList.contains(cleanedActionId)){
		    					tabsList.add(cleanedActionId);
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
	    									
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}

	    								String contentViewName = contentView.getValue();
	    								String cleanedContentViewName = GraphHelper.cleanUpForDot(contentView.getValue());
	    								
	    								if(!contentViews.contains(cleanedContentViewName)){
		    		    					contentViews += cleanedContentViewName;
		    		    					contentsList.add(cleanedContentViewName);
		    		    					nbContentViews ++;
	    								}
	    							}
	    						}
	    					}
	    						    					
	    					//Handle Form Layouts
	    					List<Layouts> layoutList = type.getLayouts();
	    					for(Layouts layouts: layoutList){
	    						Layout layout = layouts.getLayout();
	    						if(layout != null && layout.getValue() != null &&!layout.getValue().startsWith("layout@") && !typeId.endsWith("_cv")){
	    							String formLayoutName = layout.getValue().split("@")[0];
	    							String cleanedFormLayoutName = GraphHelper.cleanUpForDot(layout.getValue().split("@")[0]);
	    							if(!formLayoutsList.contains(cleanedFormLayoutName)){
	    								
	    								if(!docTypesList.contains(typeId)){
	    									docTypesList.add(typeId);
	    									nbDocTypes ++;
	    			    				}
	    								
	    		    					formLayouts += cleanedFormLayoutName;
	    		    					formLayoutsList.add(cleanedFormLayoutName);
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
		
		Collections.sort(tabsList);
		Collections.sort(docTypesList);
		Collections.sort(contentsList);
		Collections.sort(formLayoutsList);

    	if(nbTabs>0) data.put("tabs", tabsList);
    	if(nbDocTypes>0) data.put("docTypes", docTypesList);
    	if(nbContentViews>0) data.put("contentViews", contentsList);
    	if(nbFormLayouts>0) data.put("formLayouts", formLayoutsList);

		String tmpDir = Environment.getDefault().getTemp().getPath();
    	Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
    	Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, "studioviz")
    	                   : Framework.createTempDirectory(null);

        // File output
    	Path inputFilePath = Paths.get(outDirPath.toString(), "inputViewText.txt");
    	try(Writer txtFile = new FileWriter (inputFilePath.toFile())){
    		template.process(data, txtFile);
    	}
    	
    	Blob blob = new FileBlob(new File(inputFilePath.toString()), "text/plain");
    	
	    return blob;
		
	}

	@Override
	public Blob generateBusinessRulesTextFromXML(String studioJarPath, List<String> nodeList)
			throws JAXBException, CommandNotAvailable, IOException, TemplateException {
		ArrayList<String> automationList = new ArrayList<String>();
		
		Component component = getComponent(studioJarPath);   
        String studioProjectName = FilenameUtils.getBaseName(studioJarPath).replace("-"+GraphHelper.SNAPSHOT_SUFFIX, "");    	       
        Template template = initializeFreemarker("inputBusinessRulesText.ftl");
        
        JarFile file = new JarFile(studioJarPath);
        Enumeration<JarEntry> entries = file.entries();
        
        //Create temporary directory
    	String tmpDir = Environment.getDefault().getTemp().getPath();
    	Path tmpDirPath = tmpDir != null ? Paths.get(tmpDir) : null;
    	Path outDirPath = tmpDirPath != null ? Files.createTempDirectory(tmpDirPath, "studioviz")
    	                   : Framework.createTempDirectory(null);

        // Build the data-model
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("studioProjectName", studioProjectName);
        
		List<Extension> extensions = component.getExtension();
		
		ArrayList<String> userActionsList = new ArrayList<String>();
		
		ArrayList<String> eventsList = new ArrayList<String>();
		ArrayList<String> wfTasksList = new ArrayList<String>();
		
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
		    				String cleanedActionId = GraphHelper.cleanUpForDot(action.getId());

		    				if(chainId != null && !("").equals(chainId) && !(".").equals(chainId)  && !chainId.endsWith("xhtml")){
		    					String cleanedChainId = GraphHelper.cleanUpForDot(chainId);
		    					
		    					//contextual graph
		    					//skip this one if it's not in the list of Chains to display		 
		    					if(nodeList != null && !nodeList.contains(cleanedChainId)){
		    						continue;
		    					}
		    								    					
		    					String refChainId = chainId.startsWith("javascript.")? chainId.replace("javascript.", "")+".scriptedOperation" : chainId+".ops";
		    					
		    					if(!automationList.contains(cleanedChainId)){
			    					automationList.add(cleanedChainId);
			    					if(chainId.startsWith("javascript")){
				    					nbAutomationScripting ++;
				    				}else{
				    					nbAutomationChains ++;
				    				}			    					
			    				}
			    				
				    			userActionsList.add(cleanedActionId);
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

		    				String chainIdForDot = GraphHelper.cleanUpForDot(chain.getId());
		    						    				
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
	    											String exprPattern = "\"(.*)\":\"(.*)\"";
	    											Pattern expR = Pattern.compile(exprPattern);
	    											Matcher m = expR.matcher(param.getValue().trim().replace(" ", ""));
	    						    				if (m.find( )) {
	    						    						    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(1))) || mainChainIsPartOfTheNodeList){
	    						    						String cleanedSecondChain = GraphHelper.cleanUpForDot(m.group(1));
	    						    						
	    						    						secondChainIsPartOfTheNodeList = true;
	    						    						if(!automationList.contains(cleanedSecondChain)){
				    											
				    					    					automationList.add(cleanedSecondChain);
				    					    					if(chainId.startsWith("javascript")){
				    						    					nbAutomationScripting ++;
				    						    				}else{
				    						    					nbAutomationChains ++;
				    						    				}	
				    					    					
			    											}	    
	    						    						
	    						    					}
	    						    					
	    						    					if(nodeList == null || (nodeList != null && nodeList.contains(m.group(2))) || mainChainIsPartOfTheNodeList){	    						    			
	    						    						String cleanedSecondChain = GraphHelper.cleanUpForDot(m.group(2));
	    						    						
	    						    						secondChainIsPartOfTheNodeList = true;
	    						    						if(!automationList.contains(cleanedSecondChain)){
				    											
				    					    					automationList.add(cleanedSecondChain);
				    					    					if(chainId.startsWith("javascript")){
				    						    					nbAutomationScripting ++;
				    						    				}else{
				    						    					nbAutomationChains ++;
				    						    				}
				    					    					
			    											}	
	    						    					}
	    						    					
	    						    				}
	    										}else{
	    											if(nodeList == null || (nodeList != null && nodeList.contains(GraphHelper.cleanUpForDot(param.getValue()))) || mainChainIsPartOfTheNodeList){
	    												String cleanedSecondChain = GraphHelper.cleanUpForDot(param.getValue());
	    												secondChainIsPartOfTheNodeList = true;
	    												
		    											if(!automationList.contains(cleanedSecondChain)){
			    											
			    					    					automationList.add(cleanedSecondChain);
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
	    							//handle the link between an Automation chain & scripting
	    							}else if(operation.getId().startsWith("javascript.")){
	    								if(nodeList == null || (nodeList != null && nodeList.contains(GraphHelper.cleanUpForDot(operation.getId()))) || mainChainIsPartOfTheNodeList){
	    									String cleanedSecondChain = GraphHelper.cleanUpForDot(operation.getId());
	    									secondChainIsPartOfTheNodeList = true;
	    									
											if(!automationList.contains(cleanedSecondChain)){
												automationList.add(cleanedSecondChain);
							    				nbAutomationScripting ++;	
							    				
											}
	    								}
	    							}
	    						}
	    					}
	    					
	    					if(!mainChainIsPartOfTheNodeList && !secondChainIsPartOfTheNodeList){
	    						continue;
	    					}
	    					
	    					if(!automationList.contains(chainIdForDot)){
		    					
		    					automationList.add(chainIdForDot);
		    					if(chainId.startsWith("javascript")){
			    					nbAutomationScripting ++;
			    				}else{
			    					nbAutomationChains ++;
			    				}
	    					}
	    				}
	    			}catch(Exception e){
	    				logger.error("Error when getting Chains", e);
	    			}
	    			break;
	    		case EXTENSIONPOINT_ROUTE_MODEL_IMPORTER :
		    		List<TemplateResource> trList = extension.getTemplateResource();
		    		for(TemplateResource tr : trList){
		    			
		    			entries = file.entries();
		    	        while(entries.hasMoreElements()) {
		    	            JarEntry entry = entries.nextElement();
		    	            if (entry.getName().equals(tr.getPath())) {
		    	                try(ZipInputStream zipIn = new ZipInputStream(file.getInputStream(entry))) {
		    	                	ZipEntry zipEntry = zipIn.getNextEntry();
		    	                	while (zipEntry != null) {
		    	                        String filePath = outDirPath.toString() + File.separator + zipEntry.getName();
		    	                        if (!zipEntry.isDirectory()) {
		    	                            // if the entry is a file, extracts it
		    	                            GraphHelper.extractFile(zipIn, filePath);
		    	                        } else {
		    	                            // if the entry is a directory, make the directory
		    	                            File dir = new File(filePath);
		    	                            dir.mkdir();
		    	                        }
		    	                        zipIn.closeEntry();
		    	                        zipEntry = zipIn.getNextEntry();
		    	                    }
		    	                    zipIn.close();
		    	                }
		    	                break;
		    	            }
		    	        }
		    			  
		    		    //Get all the tasks under the Workflow folder
		    		    File tasksFolder = new File(outDirPath.toString()+File.separator+tr.getId());
		    		    
		    			String[] tasks = tasksFolder.list(new FilenameFilter() {
		    			  @Override
		    			  public boolean accept(File current, String name) {
		    			    return new File(current, name).isDirectory();	    
		    			  }
		    			});
		    			
		    			if(tasks != null){
			    			for(String task: tasks){			    				
			    				//Use task as the id of the node			    				
				    			//Read the content of the document.xml
				    			String xmlText = FileUtils.readFileToString(new File(outDirPath.toString()+File.separator+tr.getId()+File.separator+task+File.separator+"document.xml"));
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
					    				inputChain = GraphHelper.cleanUpForDot(nodeIC.item(0).getTextContent());
					    				
					    			}
					    			
					    			//outputChain
					    			String outputChain = "";
					    			NodeList nodeOC = doc.getElementsByTagName("rnode:outputChain");
					    			if(nodeOC != null && nodeOC.getLength()>0){
					    				outputChain = GraphHelper.cleanUpForDot(nodeOC.item(0).getTextContent());
					    			}
					    			
					    			if(nodeList != null && !nodeList.contains(inputChain) && !nodeList.contains(outputChain)){
			    						continue;
			    					}
					    			
					    			if(!("").equals(inputChain) || !("").equals(outputChain)){
					    				if(nodeList == null || (nodeList != null && nodeList.contains(inputChain))){
						    				if(!("").equals(inputChain)){
						    					if(!automationList.contains(inputChain)){
							    					
							    					if(inputChain.startsWith("javascript")){
								    					nbAutomationScripting ++;
								    				}else{
								    					nbAutomationChains ++;
								    				}
							    					
							    					automationList.add(inputChain);
						    					
							    					
						    					}
						    					
						    				}
					    				}
					    				if(nodeList == null || (nodeList != null && nodeList.contains(outputChain))){
						    				if(!("").equals(outputChain)){
						    					if(!automationList.contains(outputChain)){
							    					
							    					if(outputChain.startsWith("javascript")){
								    					nbAutomationScripting ++;
								    				}else{
								    					nbAutomationChains ++;
								    				}
							    					
							    					automationList.add(outputChain);
						    					}
						    				}
					    				}
					    				
					    				if(nodeList != null && nodeList.isEmpty()){
					    					continue;
					    				}
					    				
					    				if(nodeList == null || (nodeList != null && nodeList.contains(outputChain)) || (nodeList != null && nodeList.contains(inputChain))){
						    				if(!wfTasksList.contains(task)){
						    					wfTasksList.add(task);
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
		
		//Deal with the Event Handlers		
		entries = file.entries();
        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".evt.xml")) {
                try(InputStream is = file.getInputStream(entry)) {
	    			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    			factory.setNamespaceAware(true);
	    			DocumentBuilder db;
	    			db = factory.newDocumentBuilder();
	    			Document doc = db.parse(is);
	    			
	    			NodeList nodeId = doc.getElementsByTagName("events");
	    			String eventHandlerId = nodeId.item(0).getAttributes().getNamedItem("id").getNodeValue();
	    			String eventHandlerIdForDot = GraphHelper.cleanUpForDot(eventHandlerId);
	    			eventHandlerIdForDot += eventHandlerIdForDot;
	    			
	    			NodeList nodeChainId = doc.getElementsByTagName("chain");
	    			String chainId = nodeChainId.item(0).getTextContent();
	    			String chainIdForDot = GraphHelper.cleanUpForDot(chainId);
	    			
					if(nodeList != null && !nodeList.contains(chainIdForDot)){
						continue;
					}

					
					eventsList.add(eventHandlerIdForDot);
					nbEvents ++;

					//Normally we won't go through this
					//as we already parsed all the automation chains previously
					if(!automationList.contains(chainIdForDot)){
    					
    					automationList.add(chainIdForDot);
    					if(chainIdForDot.startsWith("javascript")){
	    					nbAutomationScripting ++;
	    				}else{
	    					nbAutomationChains ++;
	    				}
    					
    				}	    			
                } catch (ParserConfigurationException e) {
					logger.error("Error while getting Event Handlers", e);
				} catch (SAXException e) {
					logger.error("Error while getting Event Handlers", e);
				}
            }
        }
		
    	if(nbUserActions>0) data.put("userActions", userActionsList);
    	
    	Collections.sort(automationList);
    	Collections.sort(eventsList);
    	Collections.sort(wfTasksList);
    	
    	if(nbAutomationChains >0 || nbAutomationScripting >0) data.put("automationChainsAndScripting", automationList);
    	if(nbEvents>0) data.put("events", eventsList);
    	if(nbWfTasks>0) data.put("wfTasks", wfTasksList);
 
    	 // File output
    	Path inputFilePath = Paths.get(outDirPath.toString(), "inputBusinessRulesText.txt");
    	try(Writer txtFile = new FileWriter (inputFilePath.toFile())){
    		template.process(data, txtFile);
    	}
    	
    	Blob blob = new FileBlob(new File(inputFilePath.toString()), "text/plain");
    	
	    return blob;
	}
}
