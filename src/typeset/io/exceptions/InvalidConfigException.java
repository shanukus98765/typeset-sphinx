package typeset.io.exceptions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Class InvalidConfigException.
 */
public class InvalidConfigException extends RuntimeException {
	

	private static final Logger logger = LogManager.getLogger("InvalidConfigException");
	/**
	 * 
	 */
	private static final long serialVersionUID = -3451855388155807786L;

	/**
	 * Instantiates a new invalid config exception.
	 */
	public InvalidConfigException() {
		super();
	}

	/**
	 * Instantiates a new invalid config exception.
	 *
	 * @param s the s
	 */
	public InvalidConfigException(String s) {
		super(s);
	}

}