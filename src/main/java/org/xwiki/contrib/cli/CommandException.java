package org.xwiki.contrib.cli;

/**
 * Exception when then the command is not valid.
 *
 * @version $Id$
 */
class CommandException extends Exception
{
    /**
     * Create a new CommandException.
     *
     * @param message the reason why the command is invalid.
     */
    CommandException(String message)
    {
        super(message);
    }

    /**
     * Create a new CommandException.
     *
     * @param message the reason why the command is invalid.
     * @param e the cause
     */
    CommandException(String message, Throwable e)
    {
        super(message, e);
    }

}
