package io.github.roony11_1.base;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import io.github.roony11_1.enviroment.config.DriverFactory;

public abstract class BaseTest 
{
    protected WebDriver driver;
    protected JavascriptExecutor jsExecutor;

    protected static final String BASE_URL = "http://localhost:3333";
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
     * Navega a ./login y envía credenciales. No hace assert.
     * El test que lo llama decide qué verificar después.
     * Necesita un usuario registrado.
     * @param usuario Nombre de usuario registrado.
     * @param password Contraseña de usuario registrado.
     */
    protected void login (String usuario, String password)
    {
        driver.get(BASE_URL + "/login");

        driver.findElement(By.id("nombre_usuario")).sendKeys(usuario);
        driver.findElement(By.id("password")).sendKeys(password);

        driver.findElement(By.cssSelector("[data-testid='button-login']")).click();
    }

    protected record TestUser(String usuario, String correo, String password) 
    {

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
}
