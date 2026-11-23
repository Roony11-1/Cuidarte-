package io.github.roony11_1.base;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.roony11_1.config.ConfigReader;
import io.github.roony11_1.enviroment.config.DriverFactory;

public abstract class BaseTest 
{
    protected WebDriver driver;
    protected JavascriptExecutor jsExecutor;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    protected static final ConfigReader CONFIG = ConfigReader.getInstance();
    protected static final String BASE_URL = CONFIG.app("base-url");
    protected static final String API_URL  = CONFIG.app("api-url");
    protected static final String DEFAULT_PASSWORD = "123456";

    @BeforeEach 
    public void setUp()
    {
        driver = DriverFactory.usarFirefox();

        jsExecutor = (JavascriptExecutor) driver;
    }

    @AfterEach 
    public void tearDown()
    {
        if (driver != null)
            driver.quit();
    }

    protected String rutaRecurso(String rutaRelativa)
    {
        Path path = Paths.get("src/test/resources", rutaRelativa).toAbsolutePath();

        return path.toString();
    }

    /**
     * Navega a ./register y completa el formulario. No hace Assert.
     * El test que lo llama decide qué verificar después.
     * Pueden chocar los usuarios registrados, recomendados pasar datos con algún ruido.
     * @param usuario Nombre del usuario a registrar.
     * @param correo Correo del usuario a registrar.
     * @param password Contraseña del usuario a registrar.
     */
    protected void registrar(String usuario, String correo, String password)
    {
        driver.get(BASE_URL + "/registro");

        driver.findElement(By.id("nombre_usuario")).sendKeys(usuario);
        driver.findElement(By.id("correo")).sendKeys(correo);
        driver.findElement(By.id("password")).sendKeys(password);

        driver.findElement(By.cssSelector("[data-testid='button-registro-crear-cuenta']")).click();
    }


    /**
     * Navega al formulario de inicio de sesión, envía las credenciales y
     * recupera el JWT almacenado en localStorage después de autenticarse.
     *
     * No realiza assertions. El test que invoque este método decide qué
     * verificaciones ejecutar posteriormente.
     *
     * Necesita un usuario previamente registrado y que el frontend almacene
     * el JWT en localStorage bajo la clave "token".
     *
     * @param usuario Nombre de usuario registrado.
     * @param password Contraseña del usuario registrado.
     * @return TokenJWT que contiene el JWT obtenido desde localStorage.
     * @throws TimeoutException Si el token no se almacena dentro de los
     *                          10 segundos posteriores al envío del formulario.
     */
    protected TokenJWT login(String usuario, String password)
    {
        driver.get(BASE_URL + "/login");

        driver.findElement(By.id("nombre_usuario")).sendKeys(usuario);
        driver.findElement(By.id("password")).sendKeys(password);

        driver.findElement(By.cssSelector("[data-testid='button-login']")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        String token = (String) wait.until(d ->
            ((JavascriptExecutor) d).executeScript(
                "return localStorage.getItem('token')"
            )
        );

        return new TokenJWT(token);
    }

    protected record TestUser(String usuario, String correo, String password) 
    {

    }

    protected class TokenJWT
    {
        private final String token;

        public TokenJWT(String token)
        {
            this.token = token;
        }

        public String get()
        {
            return token;
        }
    }

    /**
     * Crea un usuario único con la contraseña por defecto (123456).
     */
    protected TestUser crearUsuarioUnico(String prefijo) 
    {
        return crearUsuarioUnico(prefijo, DEFAULT_PASSWORD);
    }

    /**
     * Crea un usuario único con una contraseña específica.
     */
    protected TestUser crearUsuarioUnico(String nombre, String contrasena) 
    {
        String sufijo  = String.valueOf(Instant.now().toEpochMilli());
        String usuario = nombre + sufijo;
        return new TestUser(usuario, usuario + "@test.com", contrasena);
    }

    protected  HttpResponse<String> get(String path, String token) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** Crea archivo temporal con tamaño fijo y prefijo opcional. */
    protected  static Path crearArchivo(String prueba, String nombre, int tamaño, String prefijo) throws Exception
    {
        Path tmp = Files.createTempFile(prueba + System.nanoTime() + "-", "-" + nombre);
        byte[] bytes = new byte[tamaño];
        if (prefijo != null) 
        {
            byte[] p = prefijo.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(p, 0, bytes, 0, Math.min(p.length, bytes.length));
        } 
        else
            java.util.Arrays.fill(bytes, (byte) 'A');
        if (prefijo != null && prefijo.length() < tamaño)
            java.util.Arrays.fill(bytes, prefijo.length(), tamaño, (byte) 'A');

        Files.write(tmp, bytes);
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}
