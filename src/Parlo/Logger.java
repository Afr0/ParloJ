/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo Library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo;

public class Logger 
{

        private static MessageLoggedDelegate onMessageLogged;

        public static void setOnMessageLogged(MessageLoggedDelegate onMessageLogged) 
        {
            Logger.onMessageLogged = onMessageLogged;
        }

        public static void log(String message, LogLevel level) 
        {
            LogMessage logMessage = new LogMessage(message, level);

            if (onMessageLogged != null)
                onMessageLogged.accept(logMessage);
            else
                System.out.println(logMessage.getMessage());
        }
    }