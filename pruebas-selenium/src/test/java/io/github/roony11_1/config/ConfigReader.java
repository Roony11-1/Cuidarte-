package io.github.roony11_1.config;

import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

public class ConfigReader 
{
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::-?([^}]*))?}");

    private final Map<String, Object> data;

    private ConfigReader() 
    {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("application.yml")) 
        {
            if (is == null)
                throw new IllegalStateException("application.yml no está en el classpath");

            this.data = new Yaml().load(is);
        } 
        catch (Exception e) 
        {
            throw new RuntimeException("Error cargando application.yml", e);
        }
    }

    private static class Holder 
    {
        private static final ConfigReader INSTANCE = new ConfigReader();
    }

    public static ConfigReader getInstance() 
    {
        return Holder.INSTANCE;
    }

    public String app(String key) 
    {
        return resolve("app." + key, "APP_" + key.toUpperCase().replace('-', '_'));
    }

    public String db(String key) 
    {
        return resolve("db." + key, "DB_" + key.toUpperCase());
    }

    public String selenium(String key) 
    {
        return resolve("selenium." + key, "SELENIUM_" + key.toUpperCase().replace('-', '_'));
    }

    public String security(String key) 
    {
        return resolve("security." + key, "SECURITY_" + key.toUpperCase().replace('-', '_'));
    }

    public int seleniumInt(String key) 
    {
        return Integer.parseInt(selenium(key));
    }

    public boolean seleniumBool(String key) 
    {
        return Boolean.parseBoolean(selenium(key));
    }

    /**
     * Prioridad:
     *   1. Env var directa (ej. DB_USER).
     *   2. Valor del YAML, expandiendo placeholders ${VAR:-default} si los tiene.
     */
    @SuppressWarnings("unchecked")
    private String resolve(String yamlPath, String envKey) 
    {
        String envValue = System.getenv(envKey);

        if (envValue != null && !envValue.isBlank())
            return envValue;

        // 2. Leer del YAML
        Object current = data;

        for (String part : yamlPath.split("\\.")) 
        {
            if (!(current instanceof Map)) 
                return null;
            
            current = ((Map<String, Object>) current).get(part);
            if (current == null) 
                return null;
        }

        String raw = String.valueOf(current);

        // 3. Expandir ${VAR:-default} si lo hay
        return expandPlaceholders(raw);
    }

    /**
     * Sustituye todos los ${VAR} y ${VAR:-default} del string.
     * Si la variable no existe, usa el default. Si no hay default, deja el placeholder.
     */
    private String expandPlaceholders(String value) 
    {
        if (value == null || !value.contains("${")) 
            return value;

        Matcher m = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();

        while (m.find()) 
        {
            String varName    = m.group(1).trim();

            String defaultVal = m.group(2);  // null si no hay ':' en el placeholder

            String fromEnv = System.getenv(varName);
            String replacement;

            if (fromEnv != null) 
                replacement = fromEnv;
            else if (defaultVal != null)
                replacement = defaultVal;
            else 
            {
                // Sin default y sin variable: dejamos el placeholder tal cual para que
                // el test falle con un mensaje claro (o podrías lanzar excepción aquí)
                replacement = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        m.appendTail(sb);
        return sb.toString();
    }
}
