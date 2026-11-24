package io.github.roony11_1.enviroment.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

public class DriverFactory 
{
    /**
     * Selenium Manager (incluido desde Selenium 4.6) resuelve el geckodriver
     * compatible. Desde Selenium 4.30 además soporta el Firefox snap que
     * entregan los runners Ubuntu de GitHub Actions.
     */
    public static WebDriver usarFirefox() 
    {
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("-headless");
        options.addArguments("--width=1920", "--height=1080");

        return new FirefoxDriver(options);
    }
}
