package org.aincraft.exception;

public class RepositoryException extends RuntimeException {

  public RepositoryException(Throwable cause) {
    super(cause);
  }

  public RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }

  public RepositoryException(String message) {
    super(message);
  }

  public RepositoryException() {
  }
}
