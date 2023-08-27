package Parlo;

/**
 * A message logged by Parlo.
 */
public class LogMessage 
{

        private String message;
        private LogLevel level;

        /**
         * Constructs a new log message.
         * @param message The message to log.
         * @param level The level of the message.
         */
        public LogMessage(String message, LogLevel level) 
        {
            this.message = message;
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public LogLevel getLevel() {
            return level;
        }
    }