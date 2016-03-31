/**
 * 
 */

package org.nuxeo.studioviz.service;

import java.io.IOException;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;

import com.google.gson.JsonObject;

import freemarker.template.TemplateException;


/**
 * @author mgena
 */
public interface StudioVizService  {

	
	public JsonObject generateModelGraphFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException;
	
	public Blob generateModelTextFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException;
	
	public JsonObject generateViewGraphFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException;
	
	public Blob generateViewTextFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException;
	
	public JsonObject generateBusinessRulesGraphFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException;

	public Blob generateBusinessRulesTextFromXML(String studioJarPath, List<String> nodeList) throws JAXBException, CommandNotAvailable, IOException, TemplateException;
	
}
