package net.minecraft.client.multiplayer;

import java.net.IDN;
import java.util.Hashtable;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class ServerAddress
{
    private final String ipAddress;
    private final int serverPort;

    private ServerAddress(String address, int port)
    {
        this.ipAddress = address;
        this.serverPort = port;
    }

    public String getIP()
    {
        return IDN.toASCII(this.ipAddress);
    }

    public int getPort()
    {
        return this.serverPort;
    }

    public static ServerAddress fromString(String address)
    {
        if (address == null)
        {
            return null;
        }

        String[] parts = address.split(":");

        if (address.startsWith("["))
        {
            int closingBracket = address.indexOf("]");

            if (closingBracket > 0)
            {
                String host = address.substring(1, closingBracket);
                String port = address.substring(closingBracket + 1).trim();

                if (port.startsWith(":"))
                {
                    port = port.substring(1);
                    parts = new String[] {host, port};
                }
                else
                {
                    parts = new String[] {host};
                }
            }
        }

        if (parts.length > 2)
        {
            parts = new String[] {address};
        }

        String host = parts[0];
        int port = parts.length > 1 ? parseIntWithDefault(parts[1]) : 25565;

        if (port == 25565)
        {
            String[] resolvedAddress = getServerAddress(host);
            host = resolvedAddress[0];
            port = parseIntWithDefault(resolvedAddress[1]);
        }

        return new ServerAddress(host, port);
    }

    private static String[] getServerAddress(String host)
    {
        DirContext context = null;

        try
        {
            Class.forName("com.sun.jndi.dns.DnsContextFactory");
            Hashtable<String, String> environment = new Hashtable<>();
            environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            environment.put("java.naming.provider.url", "dns:");
            environment.put("com.sun.jndi.dns.timeout.retries", "1");
            context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes("_minecraft._tcp." + host, new String[] {"SRV"});
            String[] record = attributes.get("srv").get().toString().split(" ", 4);
            return new String[] {record[3], record[2]};
        }
        catch (Throwable ignored)
        {
            return new String[] {host, Integer.toString(25565)};
        }
        finally
        {
            if (context != null)
            {
                try
                {
                    context.close();
                }
                catch (Exception ignored)
                {
                }
            }
        }
    }

    private static int parseIntWithDefault(String value)
    {
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (Exception ignored)
        {
            return 25565;
        }
    }
}
