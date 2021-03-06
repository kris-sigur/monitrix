package uk.bl.monitrix.heritrix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

import play.Logger;

/**
 * An incremental log file reader that emulates UNIX 'tail -f'-like read behavior.
 * @author Rainer Simon <rainer.simon@ait.ac.at>
 */
public class IncrementalLogfileReader {
	
	private static long TEN_MINUTES = 60 * 10000;
	
	private File logFile;
	
	private BufferedReader reader;
	
	private long linesRead = 0;
	
	private long lastModifiedValueAtLastRead = 0;
	
	private long lastSize = 0;

	public IncrementalLogfileReader(String filename) throws FileNotFoundException {
		File log = new File(filename);
		if (!log.exists())
			throw new FileNotFoundException(filename + " not found");

		this.logFile = new File(log.getAbsolutePath());
		this.lastSize = logFile.length();
		
		this.reader = new BufferedReader(new FileReader(log));
	}
	
	public String getPath() {
		return logFile.getAbsolutePath();
	}
	
	/**
	 * 
	 * @return
	 * @throws IOException
	 */
	public boolean isRenamed() throws IOException {
		// If the log has become smaller - RENAMED!
		if (logFile.length() < lastSize)
			return true;
		
		// This may work better, as the problem is the 'known' log file being renamed:
		/*
		if( logFile.getAbsolutePath() != this.getPath() ) 
			return true;
		*/

		// If the log was modified, but the reader didn't read anything in the past 10 minutes - RENAMED!
		// NOTE that this does not really work - if there are many log files, the it may take > 10 mins to get around to a log file.
		/*
		if (lastModifiedValueAtLastRead < logFile.lastModified() - TEN_MINUTES)
			return true;
		*/

		return false;
	}
	
	public void skipLines( long linesToSkip ) {
		for (long i=0; i<linesToSkip; i++) {
			try {
				reader.readLine();
				linesRead++;
			} catch (IOException e) {
				Logger.error("Exception '"+e+"' while skipping "+linesToSkip+" lines of log file: "+this.getPath());
			}
		}
	}

	/**
	 * Returns an iterator over all log entries that have not yet been consumed through
	 * this {@link IncrementalLogfileReader} instance (including those that may have been
	 * added to the underlying log file in the mean time).
	 * @return the iterator 
	 */
	public Iterator<LogFileEntry> newIterator() {
		try {
			return new FollowingLogIterator(reader);
		} catch (IOException e) {
			// Should never happen as we've already checked that the file exists
			// in the constructor!
			throw new RuntimeException(e);
		}
	}
	
	public long getNumberOfLinesRead() {
		return linesRead;
	}
	
	private class FollowingLogIterator implements Iterator<LogFileEntry> {
		
		private BufferedReader reader;
		
		private String nextLine;
		
		LogFileEntry next =  new LogFileEntry();
		
		FollowingLogIterator(BufferedReader reader) throws IOException {
			this.reader = reader;
			nextLine = reader.readLine();
			linesRead++;
		}
		
		@Override
		public boolean hasNext() {
			return nextLine != null;
		}

		@Override
		public LogFileEntry next() {
			try {
				next.init(logFile.getAbsolutePath(), nextLine);
				nextLine = reader.readLine();
				linesRead++;
				lastModifiedValueAtLastRead = logFile.lastModified();
				return next;
			} catch (IOException e) {
				// Should never happen as we've already checked that the file exists
				// in the constructor!
				throw new RuntimeException(e);
			}
		}

		@Override
		public void remove() {
			// Not supported
			throw new UnsupportedOperationException();
		}
	}	

}
