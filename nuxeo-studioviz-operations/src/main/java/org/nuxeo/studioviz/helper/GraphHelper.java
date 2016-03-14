package org.nuxeo.studioviz.helper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.connect.data.DownloadablePackage;
import org.nuxeo.connect.packages.PackageManager;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.service.CommandLineExecutorComponent;
import org.nuxeo.runtime.api.Framework;

public class GraphHelper {

    private Log logger = LogFactory.getLog(GraphHelper.class);
    public static final String SNAPSHOT_SUFFIX = "0.0.0-SNAPSHOT";
    private static final int BUFFER_SIZE = 4096;

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
	    	studioJar = studioPackage.replace("-"+SNAPSHOT_SUFFIX, "")+".jar";
	    } else {
	    	logger.info("No Studio Package found.");
	    }
	    return studioJar;
	}

	 public static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
	        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
	        byte[] bytesIn = new byte[BUFFER_SIZE];
	        int read = 0;
	        while ((read = zipIn.read(bytesIn)) != -1) {
	            bos.write(bytesIn, 0, read);
	        }
	        bos.close();
	    }
}
