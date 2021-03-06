/**
 * Copyright 2013-2019 Sylvain Cadilhac (NetFishers)
 * 
 * This file is part of Netshot.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package onl.netfishers.netshot.device.script;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import onl.netfishers.netshot.device.Device;
import onl.netfishers.netshot.device.DeviceDriver;
import onl.netfishers.netshot.device.Device.InvalidCredentialsException;
import onl.netfishers.netshot.device.Device.MissingDeviceDriverException;
import onl.netfishers.netshot.device.DeviceDriver.DriverProtocol;
import onl.netfishers.netshot.device.access.Cli;
import onl.netfishers.netshot.device.credentials.DeviceCliAccount;
import onl.netfishers.netshot.device.script.helper.JsCliHelper;
import onl.netfishers.netshot.device.script.helper.JsCliScriptOptions;
import onl.netfishers.netshot.device.script.helper.JsDeviceHelper;
import onl.netfishers.netshot.device.script.helper.JsDiagnosticHelper;
import onl.netfishers.netshot.diagnostic.Diagnostic;
import onl.netfishers.netshot.work.TaskLogger;

public class RunDiagnosticCliScript extends CliScript {
	/** The logger. */
	private static Logger logger = LoggerFactory.getLogger(RunDiagnosticCliScript.class);
	
	/** The diagnostics to execute. */
	private List<Diagnostic> diagnostics;

	/**
	 * Instantiates a JS-based script.
	 * @param code The JS code
	 */
	public RunDiagnosticCliScript(List<Diagnostic> diagnostics, boolean cliLogging) {
		super(cliLogging);
		this.diagnostics = diagnostics;
	}

	@Override
	protected void run(Session session, Device device, Cli cli, DriverProtocol protocol, DeviceCliAccount cliAccount)
			throws InvalidCredentialsException, IOException, ScriptException, MissingDeviceDriverException {

		JsCliHelper jsCliHelper = new JsCliHelper(cli, cliAccount, this.getJsLogger(), this.getCliLogger());
		TaskLogger taskLogger = this.getJsLogger();
		DeviceDriver driver = device.getDeviceDriver();
		// Filter on the device driver
		try {
			ScriptEngine engine = driver.getEngine();
			ScriptContext scriptContext = new SimpleScriptContext();
			scriptContext.setBindings(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE),
					ScriptContext.ENGINE_SCOPE);
			JsCliScriptOptions options = new JsCliScriptOptions(jsCliHelper);
			options.setDevice(new JsDeviceHelper(device, null, taskLogger, false));

			Map<String, Object> jsDiagnostics = new HashMap<String, Object>();
			for (Diagnostic diagnostic : this.diagnostics) {
				try {
					Object jsObject = diagnostic.getJsObject(device, engine, scriptContext);
					if (jsObject == null) {
						continue;
					}
					jsDiagnostics.put(diagnostic.getName(), jsObject);
				}
				catch (Exception e1) {
					logger.error("Error while preparing the diagnostic {} for JS: {}.", diagnostic.getName(), e1);
					taskLogger.error(String.format("Error while preparing the diagnostic %s for JS: '%s'.",
							diagnostic.getName(), e1.getMessage()));
				}
			}
			options.setDiagnosticHelper(new JsDiagnosticHelper(device, diagnostics, jsDiagnostics, taskLogger));

			if (jsDiagnostics.size() > 0) {
				((Invocable) engine).invokeFunction("_connect", "diagnostics", protocol.value(), options, taskLogger);
			}

		}
		catch (ScriptException e) {
			logger.error("Error while running script using driver {}.", driver.getName(), e);
			taskLogger.error(String.format("Error while running script  using driver %s: '%s'.",
					driver.getName(), e.getMessage()));
			if (e.getMessage().contains("Authentication failed")) {
				throw new InvalidCredentialsException("Authentication failed");
			}
			else {
				throw e;
			}
		}
		catch (NoSuchMethodException e) {
			logger.error("No such method while using driver {}.", driver.getName(), e);
			taskLogger.error(String.format("No such method while using driver %s to execute script: '%s'.",
					driver.getName(), e.getMessage()));
			throw new ScriptException(e);
		}
	}

}
