/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

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