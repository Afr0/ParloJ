package Parlo;

public class LogMessage 
{

        private String message;
        private LogLevel level;

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