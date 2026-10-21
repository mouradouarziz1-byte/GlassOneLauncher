using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Linq.Expressions;
using System.Reflection;
using System.Security;
using System.Security.Principal;
using System.Xml.Linq;
using Microsoft.Win32;

namespace PLCDataAnalyzer
{
    public sealed class TiaDiagnostics
    {
        public string ApiPath { get; set; }
        public string RegistryKey { get; set; }
        public bool IsOpennessGroupMember { get; set; }
    }

    public sealed class TiaService
    {
        string apiPath;
        string registryKey;
        object portal;
        bool resolverInstalled;
        readonly List<Assembly> assemblies = new List<Assembly>();

        public TiaDiagnostics GetDiagnostics()
        {
            var path = DetectInternal();
            return new TiaDiagnostics
            {
                ApiPath = path,
                RegistryKey = registryKey,
                IsOpennessGroupMember = IsMemberOfOpennessGroup()
            };
        }

        public string Detect() { return DetectInternal(); }

        string DetectInternal()
        {
            if (!string.IsNullOrWhiteSpace(apiPath) && File.Exists(Path.Combine(apiPath, "Siemens.Engineering.Base.dll")))
                return apiPath;

            registryKey = null;
            var candidates = new List<string>();
            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (var hklm = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                    using (var root = hklm.OpenSubKey(@"SOFTWARE\Siemens\Automation\Openness"))
                    {
                        if (root == null) continue;
                        foreach (var portalVersion in root.GetSubKeyNames().Where(x => x.StartsWith("21", StringComparison.OrdinalIgnoreCase)).OrderByDescending(x => x))
                        {
                            using (var pub = root.OpenSubKey(portalVersion + @"\PublicAPI"))
                            {
                                if (pub == null) continue;
                                foreach (var engineeringVersion in pub.GetSubKeyNames().OrderByDescending(x => x))
                                {
                                    using (var net = pub.OpenSubKey(engineeringVersion + @"\net48"))
                                    {
                                        if (net == null) continue;
                                        var baseDll = net.GetValue("Siemens.Engineering.Base") as string;
                                        if (string.IsNullOrWhiteSpace(baseDll)) continue;
                                        var dir = Path.GetDirectoryName(baseDll);
                                        if (!string.IsNullOrWhiteSpace(dir)) candidates.Add(dir);
                                        if (File.Exists(baseDll) && registryKey == null)
                                            registryKey = "HKLM(" + view + @")\SOFTWARE\Siemens\Automation\Openness\" + portalVersion + @"\PublicAPI\" + engineeringVersion + @"\net48";
                                    }
                                }
                            }
                        }
                    }
                }
                catch { }
            }

            candidates.Add(@"C:\Program Files\Siemens\Automation\Portal V21\PublicAPI\V21\net48");
            candidates.Add(@"C:\Program Files (x86)\Siemens\Automation\Portal V21\PublicAPI\V21\net48");
            apiPath = candidates.Distinct(StringComparer.OrdinalIgnoreCase)
                .FirstOrDefault(p => !string.IsNullOrWhiteSpace(p) && File.Exists(Path.Combine(p, "Siemens.Engineering.Base.dll")));
            return apiPath;
        }

        static bool IsMemberOfOpennessGroup()
        {
            try
            {
                var identity = WindowsIdentity.GetCurrent();
                if (identity == null || identity.Groups == null) return false;
                foreach (var sid in identity.Groups)
                {
                    try
                    {
                        var account = (NTAccount)sid.Translate(typeof(NTAccount));
                        if (account.Value.EndsWith(@"\Siemens TIA Openness", StringComparison.OrdinalIgnoreCase) ||
                            account.Value.Equals("Siemens TIA Openness", StringComparison.OrdinalIgnoreCase)) return true;
                    }
                    catch { }
                }
            }
            catch { }
            return false;
        }

        void EnsureResolver()
        {
            if (resolverInstalled) return;
            AppDomain.CurrentDomain.AssemblyResolve += ResolveEngineeringAssembly;
            resolverInstalled = true;
        }

        Assembly ResolveEngineeringAssembly(object sender, ResolveEventArgs args)
        {
            try
            {
                var requested = new AssemblyName(args.Name);
                if (requested.Name == null || !requested.Name.StartsWith("Siemens.Engineering", StringComparison.OrdinalIgnoreCase)) return null;
                if (string.IsNullOrWhiteSpace(apiPath)) DetectInternal();
                if (string.IsNullOrWhiteSpace(apiPath)) return null;
                var path = Path.Combine(apiPath, requested.Name + ".dll");
                if (!File.Exists(path)) return null;
                return Assembly.LoadFrom(path);
            }
            catch { return null; }
        }

        Assembly LoadAssembly(string fileName, Action<string> log)
        {
            var file = Path.Combine(apiPath, fileName);
            if (!File.Exists(file))
            {
                log("Assembly non presente: " + fileName);
                return null;
            }
            try
            {
                var a = Assembly.LoadFrom(file);
                if (!assemblies.Any(x => string.Equals(x.FullName, a.FullName, StringComparison.OrdinalIgnoreCase))) assemblies.Add(a);
                log("Assembly caricato: " + a.GetName().Name + "  " + a.GetName().Version);
                return a;
            }
            catch (Exception ex)
            {
                log("ERRORE caricamento " + fileName + ": " + ex.GetType().Name + " - " + ex.Message);
                throw;
            }
        }

        IEnumerable<Type> LoadableTypes(Assembly assembly, Action<string> log)
        {
            if (assembly == null) return Enumerable.Empty<Type>();
            try { return assembly.GetTypes(); }
            catch (ReflectionTypeLoadException ex)
            {
                foreach (var le in ex.LoaderExceptions.Where(x => x != null))
                    log("LoaderException " + assembly.GetName().Name + ": " + le.GetType().Name + " - " + le.Message);
                return ex.Types.Where(x => x != null);
            }
            catch (Exception ex)
            {
                log("GetTypes " + assembly.GetName().Name + ": " + ex.GetType().Name + " - " + ex.Message);
                return Enumerable.Empty<Type>();
            }
        }

        Type FindType(string shortName, Action<string> log)
        {
            var known = new[]
            {
                "Siemens.Engineering." + shortName,
                "Siemens.Engineering.HW." + shortName,
                "Siemens.Engineering.HW.Features." + shortName,
                "Siemens.Engineering.SW." + shortName,
                "Siemens.Engineering.Upload." + shortName,
                "Siemens.Engineering.Connection." + shortName
            };

            foreach (var a in assemblies.ToArray())
            {
                foreach (var full in known)
                {
                    var direct = a.GetType(full, false, false);
                    if (direct != null) return direct;
                }
                var found = LoadableTypes(a, log).FirstOrDefault(t => string.Equals(t.Name, shortName, StringComparison.Ordinal));
                if (found != null) return found;
            }

            foreach (var file in Directory.GetFiles(apiPath, "Siemens.Engineering*.dll"))
            {
                Assembly a = null;
                try { a = LoadAssembly(Path.GetFileName(file), log); }
                catch { continue; }
                foreach (var full in known)
                {
                    var direct = a.GetType(full, false, false);
                    if (direct != null) return direct;
                }
                var found = LoadableTypes(a, log).FirstOrDefault(t => string.Equals(t.Name, shortName, StringComparison.Ordinal));
                if (found != null) return found;
            }
            return null;
        }

        void EnsurePortal(Action<string> log)
        {
            if (DetectInternal() == null)
                throw new DirectoryNotFoundException("TIA Portal V21 Openness non trovato. Siemens.Engineering.Base.dll non è presente nel percorso PublicAPI\\V21\\net48.");
            if (!IsMemberOfOpennessGroup())
                throw new UnauthorizedAccessException("L'utente Windows non appartiene al gruppo locale 'Siemens TIA Openness'.");
            if (portal != null) return;

            EnsureResolver();
            log("Openness path: " + apiPath);
            if (!string.IsNullOrWhiteSpace(registryKey)) log("Openness registry: " + registryKey);

            var baseAssembly = LoadAssembly("Siemens.Engineering.Base.dll", log);
            var step7Assembly = LoadAssembly("Siemens.Engineering.Step7.dll", log);

            var portalType = baseAssembly == null ? null : baseAssembly.GetType("Siemens.Engineering.TiaPortal", false, false);
            var modeType = baseAssembly == null ? null : baseAssembly.GetType("Siemens.Engineering.TiaPortalMode", false, false);

            if (portalType == null) portalType = FindType("TiaPortal", log);
            if (modeType == null) modeType = FindType("TiaPortalMode", log);

            if (portalType == null || modeType == null)
            {
                log("TiaPortal type: " + (portalType == null ? "NON TROVATO" : portalType.FullName));
                log("TiaPortalMode type: " + (modeType == null ? "NON TROVATO" : modeType.FullName));
                throw new TypeLoadException("Le DLL TIA V21 sono state trovate ma non riesco a caricare Siemens.Engineering.TiaPortal. Guarda le LoaderException nel log.");
            }

            log("Tipo TiaPortal: " + portalType.FullName);
            log("Tipo TiaPortalMode: " + modeType.FullName);
            log("Avvio TIA Portal V21 tramite Openness...");
            log("Se compare il firewall TIA Openness, autorizza PLCDataAnalyzer.exe.");
            try
            {
                var mode = Enum.Parse(modeType, "WithUserInterface");
                portal = Activator.CreateInstance(portalType, new object[] { mode });
            }
            catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
            log("TIA Portal V21 collegato.");
        }

        public List<PlcVariable> ImportProject(string file, Action<string> log)
        {
            EnsurePortal(log);
            object project = null;
            try
            {
                log("Apro progetto: " + file);
                project = Call(Prop(portal, "Projects"), "Open", new FileInfo(Path.GetFullPath(file)));
                return Scan(project, log);
            }
            finally { Close(project); }
        }

        public List<PlcVariable> UploadStation(string ip, string interfaceName, string plcPassword, Action<string> log)
        {
            EnsurePortal(log);
            object project = null;
            var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PLCDataAnalyzer", "Uploads");
            Directory.CreateDirectory(root);
            try
            {
                project = Call(Prop(portal, "Projects"), "Create", new DirectoryInfo(root), "Upload_" + DateTime.Now.ToString("yyyyMMdd_HHmmss"));
                var providerType = FindType("StationUploadProvider", log);
                if (providerType == null) throw new TypeLoadException("StationUploadProvider non trovato nelle DLL TIA V21.");
                log("StationUploadProvider: " + providerType.FullName);

                var provider = GetService(project, providerType, log);
                if (provider == null) throw new InvalidOperationException("Il servizio StationUploadProvider non è disponibile sul progetto TIA.");

                var configuration = Prop(provider, "Configuration");
                var mode = Call(Prop(configuration, "Modes"), "Find", "PN/IE");
                if (mode == null) throw new InvalidOperationException("Modalità PN/IE non disponibile.");

                var pcInterfaces = Prop(mode, "PcInterfaces");
                var pc = SelectPcInterface(pcInterfaces, interfaceName, ip, log);
                if (pc == null) throw new InvalidOperationException("Nessuna interfaccia PN/IE adatta trovata. Controlla nel log le interfacce viste da TIA.");

                log("Interfaccia TIA selezionata: " + Convert.ToString(Prop(pc, "Name")));
                var address = Call(Prop(pc, "Addresses"), "Create", ip);
                log("Avvio Station Upload da " + ip + "...");

                var stationUpload = provider.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public)
                    .Where(x => x.Name == "StationUpload")
                    .OrderBy(x => x.GetParameters().Length)
                    .FirstOrDefault(x => x.GetParameters().Length >= 2 && x.GetParameters().Length <= 3);
                if (stationUpload == null) throw new MissingMethodException(provider.GetType().FullName, "StationUpload");

                var pars = stationUpload.GetParameters();
                var args = new object[pars.Length];
                args[0] = address;
                args[1] = MakeUploadCallback(pars[1].ParameterType, log, plcPassword);
                if (pars.Length == 3)
                {
                    if (pars[2].HasDefaultValue) args[2] = pars[2].DefaultValue;
                    else if (pars[2].ParameterType.IsEnum) args[2] = Enum.ToObject(pars[2].ParameterType, 0);
                    else args[2] = null;
                }

                object result;
                try { result = stationUpload.Invoke(provider, args); }
                catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }

                log("Upload state: " + Convert.ToString(Prop(result, "State")));
                LogUploadMessages(result, log);
                if (Prop(result, "UploadedStation") == null)
                    throw new InvalidOperationException("TIA non ha restituito una stazione caricata. Controlla password/protezione CPU, interfaccia PN/IE e messaggi nel log.");

                TryCall(project, "Save");
                log("Upload completato. Lettura variabili...");
                return Scan(project, log);
            }
            finally { Close(project); }
        }

        object SelectPcInterface(object pcs, string requested, string ip, Action<string> log)
        {
            var list = Items(pcs).ToList();
            foreach (var item in list) log("TIA PN/IE interface: " + Convert.ToString(Prop(item, "Name")));

            foreach (var item in list)
            {
                try
                {
                    var accessible = Call(item, "GetAccessibleDevices");
                    foreach (var device in Items(accessible))
                    {
                        var addr = Convert.ToString(Prop(device, "Address"));
                        log("Accessible device: " + addr + "  " + Convert.ToString(Prop(device, "Name")) + "  " + Convert.ToString(Prop(device, "DeviceSeries")));
                        if (string.Equals(addr, ip, StringComparison.OrdinalIgnoreCase)) return item;
                    }
                }
                catch (Exception ex)
                {
                    log("Scan accessible devices su " + Convert.ToString(Prop(item, "Name")) + ": " + ex.Message);
                }
            }

            if (!string.IsNullOrWhiteSpace(requested))
            {
                var exact = list.FirstOrDefault(x => string.Equals(Convert.ToString(Prop(x, "Name")), requested, StringComparison.OrdinalIgnoreCase));
                if (exact != null) return exact;
                var wanted = Normalize(requested);
                var fuzzy = list.FirstOrDefault(x => Normalize(Convert.ToString(Prop(x, "Name"))).Contains(wanted) || wanted.Contains(Normalize(Convert.ToString(Prop(x, "Name")))));
                if (fuzzy != null) return fuzzy;
            }
            return list.FirstOrDefault();
        }

        static string Normalize(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return string.Empty;
            return new string(text.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());
        }

        Delegate MakeUploadCallback(Type delegateType, Action<string> log, string password)
        {
            var invoke = delegateType.GetMethod("Invoke");
            if (invoke == null) throw new InvalidOperationException("Delegate upload non valido: " + delegateType.FullName);
            var pars = invoke.GetParameters().Select(p => Expression.Parameter(p.ParameterType, p.Name)).ToArray();
            if (pars.Length != 1) throw new InvalidOperationException("Firma callback upload inattesa: " + delegateType.FullName);
            var handler = typeof(TiaService).GetMethod("HandleUploadConfiguration", BindingFlags.Instance | BindingFlags.NonPublic);
            var body = Expression.Call(Expression.Constant(this), handler,
                Expression.Convert(pars[0], typeof(object)),
                Expression.Constant(log),
                Expression.Constant(password, typeof(string)));
            return Expression.Lambda(delegateType, body, pars).Compile();
        }

        void HandleUploadConfiguration(object configuration, Action<string> log, string password)
        {
            if (configuration == null) return;
            var type = configuration.GetType();
            var message = Convert.ToString(Prop(configuration, "Message"));
            log("Upload callback: " + type.FullName + (string.IsNullOrWhiteSpace(message) ? "" : " - " + message));

            var setPassword = type.GetMethods(BindingFlags.Instance | BindingFlags.Public)
                .FirstOrDefault(m => m.Name == "SetPassword" && m.GetParameters().Length == 1);
            if (setPassword != null)
            {
                if (string.IsNullOrEmpty(password))
                    throw new InvalidOperationException("Il PLC richiede una password di accesso in lettura. Inseriscila nel campo Password PLC.");
                var secure = new SecureString();
                foreach (var c in password) secure.AppendChar(c);
                secure.MakeReadOnly();
                setPassword.Invoke(configuration, new object[] { secure });
                log("Password PLC fornita a TIA.");
                return;
            }

            var currentSelection = type.GetProperty("CurrentSelection", BindingFlags.Instance | BindingFlags.Public);
            if (currentSelection != null && currentSelection.CanWrite && currentSelection.PropertyType.IsEnum)
            {
                var names = Enum.GetNames(currentSelection.PropertyType);
                var preferred = names.FirstOrDefault(n => n.IndexOf("Try", StringComparison.OrdinalIgnoreCase) >= 0)
                    ?? names.FirstOrDefault(n => !string.Equals(n, "NoAction", StringComparison.OrdinalIgnoreCase));
                if (preferred != null)
                {
                    currentSelection.SetValue(configuration, Enum.Parse(currentSelection.PropertyType, preferred), null);
                    log("Scelta upload automatica: " + preferred);
                }
            }
        }

        void LogUploadMessages(object result, Action<string> log)
        {
            var messages = Prop(result, "Messages");
            if (messages != null) WalkMessages(messages, 0, log);
        }

        void WalkMessages(object messages, int level, Action<string> log)
        {
            foreach (var message in Items(messages))
            {
                var text = Convert.ToString(Prop(message, "Message"));
                if (!string.IsNullOrWhiteSpace(text)) log(new string(' ', level * 2) + text);
                var child = Prop(message, "Messages");
                if (child != null) WalkMessages(child, level + 1, log);
            }
        }

        List<PlcVariable> Scan(object project, Action<string> log)
        {
            var result = new List<PlcVariable>();
            var softwareContainerType = FindType("SoftwareContainer", log);
            if (softwareContainerType == null) throw new TypeLoadException("SoftwareContainer non trovato nelle DLL Openness.");

            foreach (var device in Items(Prop(project, "Devices")))
            {
                var deviceName = Convert.ToString(Prop(device, "Name"));
                WalkHardware(device, item =>
                {
                    object container = null;
                    try { container = GetService(item, softwareContainerType, log); }
                    catch { }
                    if (container == null) return;
                    var software = Prop(container, "Software");
                    if (software == null || software.GetType().Name.IndexOf("PlcSoftware", StringComparison.OrdinalIgnoreCase) < 0) return;
                    log("PLC software trovato: " + deviceName);
                    ScanTags(software, deviceName, result);
                    ScanBlocks(software, deviceName, result, log);
                    ScanTypes(software, deviceName, result);
                });
            }

            return result.GroupBy(x => (x.Source ?? "") + "|" + (x.Name ?? "") + "|" + (x.Group ?? ""))
                .Select(g => g.First()).ToList();
        }

        void WalkHardware(object node, Action<object> action)
        {
            action(node);
            var children = Prop(node, "Items") ?? Prop(node, "DeviceItems");
            foreach (var child in Items(children)) WalkHardware(child, action);
        }

        void ScanTags(object software, string device, List<PlcVariable> output)
        {
            WalkTagGroup(Prop(software, "TagTableGroup"), device, "", output);
        }

        void WalkTagGroup(object group, string device, string path, List<PlcVariable> output)
        {
            if (group == null) return;
            foreach (var table in Items(Prop(group, "TagTables")))
            {
                var tableName = Convert.ToString(Prop(table, "Name"));
                foreach (var tag in Items(Prop(table, "Tags")))
                {
                    output.Add(new PlcVariable
                    {
                        Source = "PLC Tag",
                        Name = Convert.ToString(Prop(tag, "Name")),
                        DataType = Convert.ToString(Prop(tag, "DataTypeName")),
                        Address = Convert.ToString(Prop(tag, "LogicalAddress")),
                        Group = device + " / " + path + tableName
                    });
                }
            }
            foreach (var child in Items(Prop(group, "Groups")))
            {
                var name = Convert.ToString(Prop(child, "Name"));
                WalkTagGroup(child, device, path + name + "/", output);
            }
        }

        void ScanBlocks(object software, string device, List<PlcVariable> output, Action<string> log)
        {
            WalkBlockGroup(Prop(software, "BlockGroup"), device, "", output, log);
        }

        void WalkBlockGroup(object group, string device, string path, List<PlcVariable> output, Action<string> log)
        {
            if (group == null) return;
            foreach (var block in Items(Prop(group, "Blocks")))
            {
                var runtimeType = block.GetType().Name;
                if (runtimeType.IndexOf("DataBlock", StringComparison.OrdinalIgnoreCase) < 0) continue;
                var dbName = Convert.ToString(Prop(block, "Name"));
                output.Add(new PlcVariable { Source = "DB", Name = dbName, DataType = runtimeType, Address = "symbolic", Group = device + " / " + path });
                TryExtractBlockMembers(block, dbName, device + " / " + path, output, log);
            }
            foreach (var child in Items(Prop(group, "Groups")))
            {
                var name = Convert.ToString(Prop(child, "Name"));
                WalkBlockGroup(child, device, path + name + "/", output, log);
            }
        }

        void TryExtractBlockMembers(object block, string dbName, string group, List<PlcVariable> output, Action<string> log)
        {
            string temp = null;
            try
            {
                var export = block.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public)
                    .FirstOrDefault(m => m.Name == "Export" && m.GetParameters().Length == 2 && m.GetParameters()[0].ParameterType == typeof(FileInfo));
                if (export == null) return;
                var optionType = export.GetParameters()[1].ParameterType;
                if (!optionType.IsEnum) return;
                object option;
                try { option = Enum.Parse(optionType, "WithDefaults"); }
                catch { option = Enum.ToObject(optionType, 0); }

                temp = Path.Combine(Path.GetTempPath(), "PLCDataAnalyzer_" + Guid.NewGuid().ToString("N") + ".xml");
                export.Invoke(block, new object[] { new FileInfo(temp), option });
                if (!File.Exists(temp)) return;

                var doc = XDocument.Load(temp);
                var members = doc.Descendants().Where(x => x.Name.LocalName == "Member" && x.Attribute("Name") != null).ToList();
                foreach (var member in members)
                {
                    var chain = member.AncestorsAndSelf().Where(x => x.Name.LocalName == "Member" && x.Attribute("Name") != null)
                        .Reverse().Select(x => (string)x.Attribute("Name")).ToArray();
                    if (chain.Length == 0) continue;
                    var memberPath = string.Join(".", chain);
                    var dataType = (string)member.Attribute("Datatype") ?? (string)member.Attribute("DataType") ?? string.Empty;
                    var offset = (string)member.Attribute("Offset") ?? string.Empty;
                    output.Add(new PlcVariable
                    {
                        Source = "DB Var",
                        Name = dbName + "." + memberPath,
                        DataType = dataType,
                        Address = string.IsNullOrWhiteSpace(offset) ? dbName + "." + memberPath : dbName + "." + memberPath + " [offset " + offset + "]",
                        Group = group + dbName
                    });
                }
                if (members.Count > 0) log("DB " + dbName + ": " + members.Count + " membri estratti");
            }
            catch (TargetInvocationException ex) { log("DB " + dbName + ": export membri - " + (ex.InnerException ?? ex).Message); }
            catch (Exception ex) { log("DB " + dbName + ": export membri - " + ex.Message); }
            finally { try { if (temp != null && File.Exists(temp)) File.Delete(temp); } catch { } }
        }

        void ScanTypes(object software, string device, List<PlcVariable> output)
        {
            WalkTypeGroup(Prop(software, "TypeGroup"), device, "", output);
        }

        void WalkTypeGroup(object group, string device, string path, List<PlcVariable> output)
        {
            if (group == null) return;
            foreach (var type in Items(Prop(group, "Types")))
                output.Add(new PlcVariable { Source = "UDT", Name = Convert.ToString(Prop(type, "Name")), DataType = type.GetType().Name, Address = "symbolic", Group = device + " / " + path });
            foreach (var child in Items(Prop(group, "Groups")))
            {
                var name = Convert.ToString(Prop(child, "Name"));
                WalkTypeGroup(child, device, path + name + "/", output);
            }
        }

        object GetService(object target, Type serviceType, Action<string> log)
        {
            if (target == null || serviceType == null) return null;

            var instanceMethod = target.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public)
                .FirstOrDefault(m => m.Name == "GetService" && m.IsGenericMethodDefinition && m.GetParameters().Length == 0);
            if (instanceMethod != null)
            {
                try { return instanceMethod.MakeGenericMethod(serviceType).Invoke(target, null); }
                catch { }
            }

            foreach (var assembly in assemblies.ToArray())
            {
                foreach (var type in LoadableTypes(assembly, log))
                {
                    foreach (var method in type.GetMethods(BindingFlags.Static | BindingFlags.Public))
                    {
                        if (method.Name != "GetService" || !method.IsGenericMethodDefinition || method.GetParameters().Length != 1) continue;
                        var parameterType = method.GetParameters()[0].ParameterType;
                        if (!parameterType.IsAssignableFrom(target.GetType())) continue;
                        try { return method.MakeGenericMethod(serviceType).Invoke(null, new[] { target }); }
                        catch { }
                    }
                }
            }
            return null;
        }

        static object Prop(object obj, string name)
        {
            if (obj == null) return null;
            try { return obj.GetType().GetProperty(name, BindingFlags.Instance | BindingFlags.Public)?.GetValue(obj, null); }
            catch { return null; }
        }

        static object Call(object obj, string methodName, params object[] args)
        {
            if (obj == null) return null;
            Exception last = null;
            foreach (var method in obj.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public)
                .Where(m => m.Name == methodName && m.GetParameters().Length == args.Length))
            {
                try { return method.Invoke(obj, args); }
                catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
                catch (Exception ex) { last = ex; }
            }
            if (last != null) throw last;
            throw new MissingMethodException(obj.GetType().FullName, methodName);
        }

        static void TryCall(object obj, string methodName)
        {
            if (obj == null) return;
            try { obj.GetType().GetMethod(methodName, Type.EmptyTypes)?.Invoke(obj, null); }
            catch { }
        }

        static void Close(object obj) { TryCall(obj, "Close"); }

        static IEnumerable<object> Items(object collection)
        {
            var enumerable = collection as IEnumerable;
            if (enumerable == null) yield break;
            foreach (var item in enumerable) if (item != null) yield return item;
        }
    }
}
