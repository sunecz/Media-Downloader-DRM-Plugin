package sune.app.mediadownloader.drm;

import java.nio.file.Path;

import org.apache.log4j.AsyncAppender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLoggerFactory;

import sune.app.mediadown.util.Reflection2;

public final class DRMLog {
	
	private static final String PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%p|%C] %m%n";
	
	private static Logger loggerNrm;
	private static Logger loggerNop;
	private static Logger logger;
	
	// Forbid anyone to create an instance of this class
	private DRMLog() {
	}
	
	private static final void initializeNop() {
		if(loggerNop == null) {
			loggerNop = (new NOPLoggerFactory()).getLogger(DRMLog.class.getName());
		}
	}
	
	private static final void initializeNrm() {
		if(loggerNrm == null) {
			loggerNrm = LoggerFactory.getLogger(DRMLog.class);
		}
	}
	
	private static final org.apache.log4j.Logger getInternalLogger(Logger logger) {
		org.apache.log4j.Logger internal = Reflection2.getField(logger.getClass(), logger, "logger");
		return internal;
	}
	
	private static final org.apache.log4j.Level getInternalLevel(Level level) {
		return org.apache.log4j.Level.toLevel(level.toString());
	}
	
	private static final void addConsoleAppender(org.apache.log4j.Logger logger, Level level) {
		org.apache.log4j.Level internalLevel = getInternalLevel(level);
		ConsoleAppender appender = new ConsoleAppender();
		appender.setLayout(new PatternLayout(PATTERN)); 
		appender.setThreshold(internalLevel);
		appender.activateOptions();
		logger.addAppender(appender);
		logger.setLevel(internalLevel);
	}
	
	private static final void addFileAppender(org.apache.log4j.Logger logger, Level level, Path file) {
		org.apache.log4j.Level internalLevel = getInternalLevel(level);
		FileAppender appender = new FileAppender();
		AsyncAppender asyncAppender = new AsyncAppender();
		asyncAppender.addAppender(appender);
		asyncAppender.setLocationInfo(true);
		appender.setFile(file.toAbsolutePath().toString());
		appender.setLayout(new PatternLayout(PATTERN)); 
		appender.setThreshold(internalLevel);
		appender.activateOptions();
		logger.addAppender(asyncAppender);
		logger.setLevel(internalLevel);
	}
	
	private static final void setLoggerPath(Level level, Path file) {
		org.apache.log4j.Logger internal = getInternalLogger(loggerNrm);
		internal.removeAllAppenders();
		if(file == null) addConsoleAppender(internal, level);
		else addFileAppender(internal, level, file);
	}
	
	public static final void enable(boolean isDebug, Path file) {
		enable(isDebug ? Level.DEBUG : Level.ERROR, file);
	}
	
	public static final void enable(Level level, Path file) {
		initializeNrm();
		setLoggerPath(level, file);
		logger = loggerNrm;
	}
	
	public static final void disable() {
		initializeNop();
		logger = loggerNop;
	}
	
	public static final Logger get() {
		if(logger == null)
			throw new IllegalStateException("Logger not initialized.");
		return logger;
	}
	
	public static final void disableInternalWarnings() {
		// Disable warning log messages by Log4j
		org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.ERROR);
	}
}