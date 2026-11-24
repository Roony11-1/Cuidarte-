package io.github.roony11_1.enviroment.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import io.github.bonigarcia.wdm.WebDriverManager;

public class DriverFactory
{
    /**
     * Crea el driver según {@code SELENIUM_BROWSER}. Por defecto usa Firefox para
     * conservar el entorno de desarrollo local; el pipeline define
     * {@code SELENIUM_BROWSER=chrome} porque el Firefox de los runners de Ubuntu
     * llega como snap y requiere Selenium Manager ≥ 4.30, mientras que Chrome se
     * resuelve de forma nativa.
     */
    public static WebDriver crear()
    {
        return "chrome".equalsIgnoreCase(System.getenv("SELENIUM_BROWSER"))
            ? usarChrome()
            : usarFirefox();
    }

    public static WebDriver usarFirefox() 
    {
        WebDriverManager.firefoxdriver().setup();

        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("-headless");
        options.addArguments("--width=1920", "--height=1080");

        return new FirefoxDriver(options);
    }

    public static WebDriver usarChrome()
    {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        return new ChromeDriver(options);
    }
}
