package io.typeset.sphinx.readers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.typeset.sphinx.exceptions.InvalidConfigException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigReader {

	public static String inputDir = null;
	public static String outputDir = null;
	public static boolean generateClasses = false;
	public static boolean debugMode = false;
	public static List<String> pageImplictFunc = new ArrayList<String>();
	public static List<String> intermImplictFunc = new ArrayList<String>();
	public static List<String> controlImplictFunc = new ArrayList<String>();
	public static List<String> requiresData = new ArrayList<String>();
	public static List<String> tests = new ArrayList<String>();
	public static String homepage = null;
	private static final Logger logger = LogManager.getLogger("ConfigReader");

	public static void read(String filename) {

		logger.debug("Reading config file name : " + filename);
		Properties prop = new Properties();
		InputStream input = null;

		try {

			input = new FileInputStream(filename);

			// load a properties file
			prop.load(input);

			inputDir = prop.getProperty("input-dir").trim();
			if(!inputDir.startsWith("/")) { // if not an absolute path
				inputDir = System.getProperty("user.dir") + File.separator + inputDir;
			}
			
			
			outputDir = prop.getProperty("output-dir").trim();
			if(!outputDir.startsWith("/")) {
				outputDir = System.getProperty("user.dir") + File.separator + outputDir;
			}
			String generateClassesStr = prop.getProperty("generate-classes").trim();
			if (generateClassesStr != null && generateClassesStr.toLowerCase().equals("true")) {
				generateClasses = true;
			}
			String debugModeStr = prop.getProperty("debug-mode").trim();
			if (debugModeStr != null && debugModeStr.toLowerCase().equals("true")) {
				debugMode = true;
			}
			logger.debug("Input directory : " + inputDir);
			logger.debug("Output directory : " + outputDir);
			logger.debug("Generate classes : " + generateClasses);
			logger.debug("Debug mode : " + debugMode);

			if (inputDir != null) {
				File file = new File(inputDir);
				if (!file.isDirectory()) {
					logger.error("Invalid input directory " + inputDir);
					throw new InvalidConfigException("Invalid input directory " + inputDir);
				}
			} else {
				logger.error("Input directory null");
				throw new InvalidConfigException("Input directory null");
			}

			if (outputDir == null) {
				logger.debug("Input directory null");
				throw new InvalidConfigException("Input directory null");
			}
			
			homepage = prop.getProperty("homepage").trim();
			if(homepage == null) {
				logger.error("Homepage is null");
				throw new InvalidConfigException("Homepage is null");
			}

			String implicit_str = prop.getProperty("page-implicit").trim();
			List<String> tempList = listify(implicit_str);
			if (tempList != null) {
				pageImplictFunc.addAll(tempList);
			}
			if (pageImplictFunc.size() <= 0) {
				logger.error("Insufficient number of page assertions provided");
				throw new InvalidConfigException("Insufficient number of page assertions provided");
			}

			implicit_str = prop.getProperty("interm-implicit").trim();
			tempList = listify(implicit_str);
			if (tempList != null) {
				intermImplictFunc.addAll(tempList);
			}
			if (intermImplictFunc.size() <= 0) {
				logger.error("Insufficient number of screen/app/widget assertions provided");
				throw new InvalidConfigException("Insufficient number of screen/app/widget assertions provided");
			}

			implicit_str = prop.getProperty("control-implicit").trim();
			tempList = listify(implicit_str);
			if (tempList != null) {
				controlImplictFunc.addAll(tempList);
			}
			if (controlImplictFunc.size() <= 0) {
				logger.error("Insufficient number of control assertions provided");
				throw new InvalidConfigException("Insufficient number of control assertions provided");
			}

			implicit_str = prop.getProperty("requires-data").trim();
			tempList = listify(implicit_str);
			if (tempList != null) {
				requiresData.addAll(tempList);
			}
			if (requiresData.size() <= 0) {
				logger.error("Insufficient number of control assertions provided");
				throw new InvalidConfigException("Insufficient number of control assertions provided");
			}

			String test_str = prop.getProperty("tests").trim();
			List<String> testList = listify(test_str);
			if (testList != null) {
				tests.addAll(testList);
			} else {
				logger.error("no tests found");
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (NullPointerException ex) {
			throw new InvalidConfigException();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static List<String> listify(String implicit_str) {
		if (implicit_str == null) {
			return null;
		}

		List<String> tempList = new ArrayList<String>();
		String[] list = implicit_str.split(",");
		for (String l : list) {
			tempList.add(l.trim());
		}
		return tempList;
	}

}
