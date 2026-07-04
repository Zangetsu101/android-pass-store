## Bridging the Gap: How to fix ADB & Gradle in WSL2
Developing Android apps in WSL2 is a dream—until you try to connect a physical device. You quickly realize that WSL2 lives in a virtualized bubble, and your Windows ADB server is on the outside.
If you’ve seen the dreaded Connection timed out or Cannot reach ADB server during a Gradle build, this guide is for you.
## 1. Prepare the Windows Host
By default, Windows ADB only talks to Windows. We need to tell it to listen to the "outside" (WSL2).

   1. Kill existing servers: Open PowerShell and run adb kill-server.
   2. Start the Listener: Run adb -a nodaemon server start.
   * Keep this window open! The -a flag tells ADB to listen on all interfaces.
   3. The Firewall Hole: WSL2 traffic looks like external traffic to Windows. You must allow it:
   
   New-NetFirewallRule -DisplayName "ADB for WSL" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5037 -RemoteAddress 172.16.0.0/12
   
   
## 2. The "Secret Sauce": The Socat Relay
Even if adb devices works in WSL, gradlew installDebug often fails. This is because Gradle is hardcoded to look at 127.0.0.1. Since your server is at 172.x.x.x, Gradle gets lost.
We solve this by creating a proxy inside Linux that forwards localhost traffic to Windows.

   1. Install Socat: sudo apt install socat
   2. Run the Relay:
   
   WINDOWS_IP=$(ip route show default | awk '{print $3}')
   socat -d -d TCP-LISTEN:5037,reuseaddr,fork TCP:$WINDOWS_IP:5037
   
   
Now, when Gradle knocks on 127.0.0.1:5037, socat picks up the phone and transfers the call to Windows.
## 3. Verification Tips (Don't skip these!)## The "Version Match" Trap
If your Linux ADB version is 34.0.1 and Windows is 35.0.0, they will fight. One will kill the other's server in an infinite loop.

* Check: Run adb version in both. They must be identical.

## The Connectivity Test
Don't guess; test the pipe. Use nc (netcat) in WSL to see if the Windows port is open:

nc -vz $(ip route show default | awk '{print $3}') 5037


* Success: Connection to ... 5037 port [tcp/*] succeeded!
* Hang/Timeout: Your Windows Firewall is still blocking the connection.

## The "Is it Listening?" Check
On Windows, verify ADB is actually bound to 0.0.0.0 (all interfaces) and not just 127.0.0.1:

netstat -an | findstr 5037

You want to see 0.0.0.0:5037 LISTENING.
## 4. Automation (The "Set it and Forget it")
Add this to your ~/.bashrc to start the relay automatically:

# Auto-bridge ADB to Windowsif ! pgrep -x "socat" > /dev/null; then
    WINDOWS_IP=$(ip route show default | awk '{print $3}')
    socat TCP-LISTEN:5037,reuseaddr,fork TCP:$WINDOWS_IP:5037 &> /dev/null &fi

------------------------------
Happy coding! Your WSL2 environment is now officially "device-aware."


