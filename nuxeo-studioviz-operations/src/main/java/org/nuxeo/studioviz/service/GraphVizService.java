/**
 * 
 */

package org.nuxeo.studioviz.service;

import java.io.IOException;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;


/**
 * @author mgena
 */
public interface GraphVizService {

    public Blob generate(Blob blob, String inputFilePath, String outputFilePath, String format) throws CommandNotAvailable, IOException;
    
}
