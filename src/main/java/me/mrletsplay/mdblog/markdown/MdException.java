package me.mrletsplay.mdblog.markdown;

public class MdException extends Exception {

	private static final long serialVersionUID = 2453345940664448784L;

	public MdException() {
		super();
	}

	public MdException(String message, Throwable cause) {
		super(message, cause);
	}

	public MdException(String message) {
		super(message);
	}

	public MdException(Throwable cause) {
		super(cause);
	}

}
