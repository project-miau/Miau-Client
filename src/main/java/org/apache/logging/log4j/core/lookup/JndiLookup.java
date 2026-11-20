package org.apache.logging.log4j.core.lookup;

import org.apache.logging.log4j.core.LogEvent;

public class JndiLookup implements StrLookup
{
    public JndiLookup()
    {
    }

    @Override
    public String lookup(String key)
    {
        return null;
    }

    @Override
    public String lookup(LogEvent event, String key)
    {
        return null;
    }
}
