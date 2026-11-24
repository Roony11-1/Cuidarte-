package io.github.roony11_1.api;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP mínimo para preparar datos de prueba a través de la API real.
 *
 * <p>Se usa cuando la ficha pide explícitamente un endpoint (por ejemplo
 * {@code POST /examenes} o {@code DELETE /examenes/:id}) y no interesa repetir
 * el flujo completo de la UI solo para el montaje del escenario.</p>
 */
public class ApiClient
{
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final Pattern ID =
        Pattern.compile("\"id\"\\s*:\\s*\"?(\\d+)\"?");

    private static final Pattern TOKEN =
        Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final String baseUrl;

    public ApiClient(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public HttpResponse<String> get(String path, String token) throws Exception
    {
        HttpRequest request = base(path, token).GET().build();

        return enviar(request);
    }

    public HttpResponse<String> postJson(String path, String token, String json)
        throws Exception
    {
        HttpRequest request = base(path, token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();

        return enviar(request);
    }

    public HttpResponse<String> postMultipartPdf(
        String path, String token, Map<String, String> campos, Path archivo)
        throws Exception
    {
        String boundary = "----Cuidarte" + UUID.randomUUID();

        HttpRequest request = base(path, token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(
                construirMultipart(boundary, campos, archivo)))
            .build();

        return enviar(request);
    }

    public HttpResponse<String> delete(String path, String token) throws Exception
    {
        HttpRequest request = base(path, token).DELETE().build();

        return enviar(request);
    }

    /** Inicia sesión vía API y devuelve el JWT de la respuesta. */
    public String iniciarSesion(String usuario, String password) throws Exception
    {
        String json = "{\"nombre_usuario\":\"" + escaparJson(usuario)
            + "\",\"password\":\"" + escaparJson(password) + "\"}";

        HttpResponse<String> respuesta = postJson("/autenticacion/login", null, json);

        if (respuesta.statusCode() != 200)
            throw new IllegalStateException("Login falló para " + usuario
                + ": HTTP " + respuesta.statusCode() + " Body=" + respuesta.body());

        Matcher matcher = TOKEN.matcher(respuesta.body());

        if (!matcher.find())
            throw new IllegalStateException("La respuesta de login no contiene token: "
                + respuesta.body());

        return matcher.group(1);
    }

    /** Extrae el primer {@code "id"} numérico del cuerpo JSON. */
    public static long extraerId(String body)
    {
        Matcher matcher = ID.matcher(body);

        if (!matcher.find())
            throw new IllegalStateException("La respuesta no contiene un id: " + body);

        return Long.parseLong(matcher.group(1));
    }

    private HttpRequest.Builder base(String path, String token)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30));

        if (token != null && !token.isBlank())
            builder.header("Authorization", "Bearer " + token);

        return builder;
    }

    private static HttpResponse<String> enviar(HttpRequest request) throws Exception
    {
        return HTTP.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private static byte[] construirMultipart(
        String boundary, Map<String, String> campos, Path archivo) throws Exception
    {
        byte[] contenido = Files.readAllBytes(archivo);
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        for (Map.Entry<String, String> campo : campos.entrySet())
        {
            escribir(body, "--" + boundary + "\r\n");
            escribir(body, "Content-Disposition: form-data; name=\""
                + campo.getKey() + "\"\r\n\r\n");
            escribir(body, campo.getValue() + "\r\n");
        }

        escribir(body, "--" + boundary + "\r\n");
        escribir(body, "Content-Disposition: form-data; name=\"documento\"; filename=\""
            + archivo.getFileName() + "\"\r\n");
        escribir(body, "Content-Type: application/pdf\r\n\r\n");
        body.write(contenido);
        escribir(body, "\r\n");
        escribir(body, "--" + boundary + "--\r\n");

        return body.toByteArray();
    }

    private static void escribir(ByteArrayOutputStream body, String texto)
    {
        body.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String escaparJson(String valor)
    {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
