/* This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 United States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 * 
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS".  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof including, but
 * not limited to, the correctness, accuracy, reliability or usefulness of
 * the software.
 * 
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement.
 */
package gov.nist.appvet.tool.synchtest;

import gov.nist.appvet.tool.synchtest.util.FileUtil;
import gov.nist.appvet.tool.synchtest.util.HttpUtil;
import gov.nist.appvet.tool.synchtest.util.Logger;
import gov.nist.appvet.tool.synchtest.util.Protocol;
import gov.nist.appvet.tool.synchtest.util.ReportFormat;
import gov.nist.appvet.tool.synchtest.util.ReportUtil;
import gov.nist.appvet.tool.synchtest.util.ToolStatus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * This class implements a synchronous tool service.
 */
public class Service extends HttpServlet {

	private static final int SLEEP_TIME = 100000; // In milliseconds
	private static final long serialVersionUID = 1L;
	private static final String reportName = "report";
	private static final Logger log = Properties.log;
	private static String appDirPath = null;
	private String appFilePath = null;
	private String reportFilePath = null;
	private String fileName = null;
	private String appId = null;
	private String command = null;
	private StringBuffer reportBuffer = null;

	/** CHANGE (START): Add expected HTTP request parameters **/
	/** CHANGE (END): Add expected HTTP request parameters **/
	public Service() {
		super();
	}

	/*
	 * // AppVet tool services will rarely use HTTP GET protected void
	 * doGet(HttpServletRequest request, HttpServletResponse response) throws
	 * ServletException, IOException {
	 */

	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		// Get received HTTP parameters and file upload
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		List items = null;
		FileItem fileItem = null;

		try {
			items = upload.parseRequest(request);
		} catch (FileUploadException e) {
			e.printStackTrace();
		}

		// Get received items
		Iterator iter = items.iterator();
		FileItem item = null;

		while (iter.hasNext()) {
			item = (FileItem) iter.next();
			if (item.isFormField()) {
				// Get HTML form parameters
				String incomingParameter = item.getFieldName();
				String incomingValue = item.getString();
				if (incomingParameter.equals("appid")) {
					appId = incomingValue;
					log.info("Received app ID: " + appId);
				}
				/** CHANGE (START): Get other tools-specific form parameters **/
				/** CHANGE (END): Get other tools-specific form parameters **/
			} else {
				// item should now hold the received file
				if (item != null) {
					fileItem = item;
					log.info("Received file: " + fileItem.getName());
				}
			}
		}

		if (appId == null) {
			// All tool services require an AppVet app ID
			log.error("Received null app ID. Returning HTTP 400");
			HttpUtil.sendHttp400(response, "No app ID specified");
			return;
		}

		if (fileItem != null) {
			// Get app file
			fileName = FileUtil.getFileName(fileItem.getName());
			if (!fileName.endsWith(".apk")) {
				log.error("Received invalid app file. Returning HTTP 400");
				HttpUtil.sendHttp400(response,
						"Invalid app file: " + fileItem.getName());
				return;
			}
			// Create app directory
			appDirPath = Properties.TEMP_DIR + "/" + appId;
			File appDir = new File(appDirPath);
			if (!appDir.exists()) {
				appDir.mkdir();
			}
			// Create report path
			reportFilePath = Properties.TEMP_DIR + "/" + appId + "/"
					+ reportName + "." + Properties.reportFormat.toLowerCase();

			appFilePath = Properties.TEMP_DIR + "/" + appId + "/" + fileName;
			log.debug("App file path: " + appFilePath);
			if (!FileUtil.saveFileUpload(fileItem, appFilePath)) {
				log.error("Could not save file. Returning HTTP 500");
				HttpUtil.sendHttp500(response, "Could not save uploaded file");
				return;
			}
			log.debug("Saved app file");
		} else {
			HttpUtil.sendHttp400(response, "No app was received.");
			return;
		}

		// Use if reading command from ToolProperties.xml. Otherwise,
		// comment-out if using custom command (called by customExecute())
		//command = getCommand();

		/*
		 * CHANGE: Select either execute() to execute a native OS command or
		 * customExecute() to execute your own custom code. Make sure that the
		 * unused method call is commented-out.
		 */
		reportBuffer = new StringBuffer();
		boolean succeeded = customExecute(reportBuffer);
		if (!succeeded) {
			log.error("Error detected: " + reportBuffer.toString());
			String errorReport = ReportUtil
					.getHtmlReport(
							response,
							fileName,
							ToolStatus.ERROR,
							reportBuffer.toString());
			// Send report to AppVet
			if (Properties.protocol.equals(Protocol.SYNCHRONOUS.name())) {
				// Send back ASCII in HTTP Response
				ReportUtil.sendInHttpResponse(response, errorReport,
						ToolStatus.ERROR);
			} 
			return;
		}

		// Analyze report and generate tool status
		log.info("Analyzing report for " + appFilePath);
		//		ToolStatus risk = ReportUtil.analyzeReport(reportBuffer
		//				.toString());
		ToolStatus risk = ToolStatus.LOW;  // Just set to LOW for testing
		log.info("Result: " + risk.name());
		String reportContent = null;

		// Get report
		if (Properties.reportFormat.equals(ReportFormat.HTML.name())) {
			reportContent = ReportUtil
					.getHtmlReport(
							response,
							fileName,
							risk,
							reportBuffer.toString());
		} 
		//		else if (Properties.reportFormat.equals(ReportFormat.TXT.name())) {
		//			reportContent = getTxtReport();
		//		} else if (Properties.reportFormat.equals(ReportFormat.PDF.name())) {
		//			reportContent = getPdfReport();
		//		} else if (Properties.reportFormat.equals(ReportFormat.JSON.name())) {
		//			reportContent = getJsonReport();
		//		}

		// If report is null or empty, stop processing
		if (reportContent == null || reportContent.isEmpty()) {
			log.error("Tool report is null or empty");
			return;
		} else {
			log.info("Report generated");
		}

		// Send report to AppVet
		if (Properties.protocol.equals(Protocol.SYNCHRONOUS.name())) {
			// Send back ASCII in HTTP Response
			ReportUtil.sendInHttpResponse(response, reportContent, risk);
		} 

		// Clean up
		if (!Properties.keepApps) {
			if (FileUtil.deleteDirectory(new File(appDirPath))) {
				log.debug("Deleted " + appFilePath);
			} else {
				log.error("Could not delete " + appFilePath);
			}
		}
		log.info("Done processing app " + appId);
		reportBuffer = null;
		System.gc();
	}

	public String getCommand() {
		// Get command from ToolProperties.xml file
		String cmd1 = Properties.command;
		String cmd2 = null;
		if (cmd1.indexOf(Properties.APP_FILE_PATH) > -1) {
			// Add app file path
			cmd2 = cmd1.replace(Properties.APP_FILE_PATH, appFilePath);
		} else {
			cmd2 = cmd1;
		}

		log.debug("full command: " + cmd2);
		return cmd2;
	}

	private static boolean customExecute(StringBuffer output) {
		try {
			log.info("Executing sleep test");
			Thread.sleep(SLEEP_TIME);
			output.append("Android SynchTest Results: OK");
			log.info("Ending sleep test");
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static class IOThreadHandler extends Thread {
		private InputStream inputStream;
		private StringBuffer output = new StringBuffer();
		private static final String lineSeparator = System
				.getProperty("line.separator");;

				IOThreadHandler(InputStream inputStream) {
					this.inputStream = inputStream;
				}

				public void run() {
					Scanner br = null;
					br = new Scanner(new InputStreamReader(inputStream));
					String line = null;
					while (br.hasNextLine()) {
						line = br.nextLine();
						output.append(line + lineSeparator);
					}
					br.close();

				}

				public StringBuffer getOutput() {
					return output;
				}
	}

	// TODO
	public String getTxtReport() {
		return null;
	}

	// TODO
	public String getPdfReport() {
		return null;
	}

	// TODO
	public String getJsonReport() {
		return null;
	}
}
