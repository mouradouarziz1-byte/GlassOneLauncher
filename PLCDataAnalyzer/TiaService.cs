using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Linq.Expressions;
using System.Reflection;
using Microsoft.Win32;

namespace PLCDataAnalyzer
{
    public sealed class TiaService
    {
        string apiPath; readonly List<Assembly> assemblies=new List<Assembly>(); object portal;

        public string Detect()
        {
            if(!string.IsNullOrEmpty(apiPath) && Directory.Exists(apiPath)) return apiPath;
            var paths=new List<string>();
            using(var k=Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Siemens\Automation\Openness\21.0\PublicAPI"))
            if(k!=null) foreach(var v in k.GetSubKeyNames()) using(var n=k.OpenSubKey(v+@"\net48"))
            { var f=n==null?null:n.GetValue("Siemens.Engineering.Base") as string; if(!string.IsNullOrWhiteSpace(f)) paths.Add(Path.GetDirectoryName(f)); }
            paths.Add(@"C:\Program Files\Siemens\Automation\Portal V21\PublicAPI\V21\net48");
            apiPath=paths.FirstOrDefault(p=>!string.IsNullOrWhiteSpace(p)&&File.Exists(Path.Combine(p,"Siemens.Engineering.Base.dll")));
            return apiPath;
        }

        public List<PlcVariable> ImportProject(string file,Action<string> log)
        {
            EnsurePortal(log); object project=null;
            try { project=Call(Prop(portal,"Projects"),"Open",new FileInfo(Path.GetFullPath(file))); return Scan(project,log); }
            finally { Close(project); }
        }

        public List<PlcVariable> UploadStation(string ip,string interfaceName,Action<string> log)
        {
            EnsurePortal(log); object project=null;
            var dir=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),"PLCDataAnalyzer","Uploads"); Directory.CreateDirectory(dir);
            try
            {
                project=Call(Prop(portal,"Projects"),"Create",new DirectoryInfo(dir),"Upload_"+DateTime.Now.ToString("yyyyMMdd_HHmmss"));
                var providerType=FindType("StationUploadProvider"); if(providerType==null) throw new InvalidOperationException("StationUploadProvider non trovato");
                var provider=GetService(project,providerType); if(provider==null) throw new InvalidOperationException("StationUploadProvider non disponibile");
                var mode=Call(Prop(Prop(provider,"Configuration"),"Modes"),"Find","PN/IE"); if(mode==null) throw new InvalidOperationException("PN/IE non disponibile");
                var pcs=Prop(mode,"PcInterfaces"); object pc=null;
                if(!string.IsNullOrWhiteSpace(interfaceName)) try { pc=Call(pcs,"Find",interfaceName,1); } catch { }
                if(pc==null) pc=Items(pcs).FirstOrDefault(); if(pc==null) throw new InvalidOperationException("Nessuna interfaccia PN/IE trovata");
                log("Interfaccia: "+(Prop(pc,"Name")??pc.ToString()));
                var address=Call(Prop(pc,"Addresses"),"Create",ip); log("Upload da "+ip+"...");
                var m=provider.GetType().GetMethods().Where(x=>x.Name=="StationUpload").OrderBy(x=>x.GetParameters().Length).FirstOrDefault(x=>x.GetParameters().Length>=2);
                if(m==null) throw new MissingMethodException("StationUpload");
                var ps=m.GetParameters(); var a=new object[ps.Length]; a[0]=address; a[1]=NoOp(ps[1].ParameterType);
                for(int i=2;i<a.Length;i++) a[i]=Type.Missing;
                object result; try { result=m.Invoke(provider,a); } catch(TargetInvocationException ex){ throw ex.InnerException??ex; }
                log("Upload state: "+Prop(result,"State"));
                if(Prop(result,"UploadedStation")==null) throw new InvalidOperationException("TIA non ha restituito una stazione caricata. Verifica protezioni CPU e messaggi TIA.");
                TryCall(project,"Save"); return Scan(project,log);
            }
            finally { Close(project); }
        }

        void EnsurePortal(Action<string> log)
        {
            if(Detect()==null) throw new DirectoryNotFoundException("TIA Portal V21 Openness non trovato"); if(portal!=null) return;
            AppDomain.CurrentDomain.AssemblyResolve+=(s,e)=>Resolve(e.Name); Load("Siemens.Engineering.Base.dll"); Load("Siemens.Engineering.Step7.dll");
            var pt=FindType("TiaPortal"); var mt=FindType("TiaPortalMode"); if(pt==null||mt==null) throw new TypeLoadException("API TIA V21 non caricata");
            portal=Activator.CreateInstance(pt,new[]{Enum.Parse(mt,"WithUserInterface")}); log("TIA Portal V21 avviato");
        }
        Assembly Resolve(string name){ var f=Path.Combine(apiPath,new AssemblyName(name).Name+".dll"); return File.Exists(f)?Assembly.LoadFrom(f):null; }
        void Load(string name){ var f=Path.Combine(apiPath,name); if(!File.Exists(f)) return; var a=Assembly.LoadFrom(f); if(!assemblies.Contains(a)) assemblies.Add(a); }
        Type FindType(string name)
        {
            foreach(var f in Directory.GetFiles(apiPath,"Siemens.Engineering*.dll")) try{Load(Path.GetFileName(f));}catch{}
            foreach(var a in assemblies) try{ var t=a.GetTypes().FirstOrDefault(x=>x.Name==name); if(t!=null)return t; }catch{}
            return null;
        }

        List<PlcVariable> Scan(object project,Action<string> log)
        {
            var outList=new List<PlcVariable>(); var scType=FindType("SoftwareContainer"); if(scType==null) throw new TypeLoadException("SoftwareContainer non trovato");
            foreach(var d in Items(Prop(project,"Devices")))
            {
                var dn=Convert.ToString(Prop(d,"Name")); WalkDevice(d,item=>
                {
                    object c=null; try{c=GetService(item,scType);}catch{} if(c==null)return;
                    var sw=Prop(c,"Software"); if(sw==null||sw.GetType().Name.IndexOf("PlcSoftware",StringComparison.OrdinalIgnoreCase)<0)return;
                    log("PLC software: "+dn); ScanTags(sw,dn,outList); ScanBlocks(sw,dn,outList); ScanTypes(sw,dn,outList);
                });
            }
            return outList.GroupBy(x=>(x.Source??"")+"|"+(x.Name??"")+"|"+(x.Group??"")).Select(g=>g.First()).ToList();
        }
        void WalkDevice(object n,Action<object> f){ f(n); foreach(var x in Items(Prop(n,"DeviceItems"))) WalkDevice(x,f); }
        void ScanTags(object sw,string device,List<PlcVariable> r){ WalkTagGroup(Prop(sw,"TagTableGroup"),device,"",r); }
        void WalkTagGroup(object g,string device,string path,List<PlcVariable> r)
        {
            if(g==null)return; foreach(var t in Items(Prop(g,"TagTables")))
            { var tn=Convert.ToString(Prop(t,"Name")); foreach(var x in Items(Prop(t,"Tags"))) r.Add(new PlcVariable{Source="PLC Tag",Name=Convert.ToString(Prop(x,"Name")),DataType=Convert.ToString(Prop(x,"DataTypeName")),Address=Convert.ToString(Prop(x,"LogicalAddress")),Group=device+" / "+path+tn}); }
            foreach(var x in Items(Prop(g,"Groups"))){ var n=Convert.ToString(Prop(x,"Name")); WalkTagGroup(x,device,path+n+"/",r); }
        }
        void ScanBlocks(object sw,string device,List<PlcVariable> r){ WalkBlocks(Prop(sw,"BlockGroup"),device,"",r); }
        void WalkBlocks(object g,string device,string path,List<PlcVariable> r)
        {
            if(g==null)return; foreach(var b in Items(Prop(g,"Blocks")))
            { var type=b.GetType().Name; if(type.IndexOf("DataBlock",StringComparison.OrdinalIgnoreCase)>=0) r.Add(new PlcVariable{Source="DB",Name=Convert.ToString(Prop(b,"Name")),DataType=type,Address="symbolic",Group=device+" / "+path}); }
            foreach(var x in Items(Prop(g,"Groups"))){ var n=Convert.ToString(Prop(x,"Name")); WalkBlocks(x,device,path+n+"/",r); }
        }
        void ScanTypes(object sw,string device,List<PlcVariable> r){ WalkTypes(Prop(sw,"TypeGroup"),device,"",r); }
        void WalkTypes(object g,string device,string path,List<PlcVariable> r)
        {
            if(g==null)return; foreach(var t in Items(Prop(g,"Types"))) r.Add(new PlcVariable{Source="UDT",Name=Convert.ToString(Prop(t,"Name")),DataType=t.GetType().Name,Address="symbolic",Group=device+" / "+path});
            foreach(var x in Items(Prop(g,"Groups"))){ var n=Convert.ToString(Prop(x,"Name")); WalkTypes(x,device,path+n+"/",r); }
        }

        object GetService(object target,Type service)
        {
            var m=target.GetType().GetMethods().FirstOrDefault(x=>x.Name=="GetService"&&x.IsGenericMethodDefinition&&x.GetParameters().Length==0);
            if(m!=null)return m.MakeGenericMethod(service).Invoke(target,null);
            foreach(var a in assemblies) { Type[] ts; try{ts=a.GetTypes();}catch{continue;} foreach(var t in ts) foreach(var x in t.GetMethods(BindingFlags.Static|BindingFlags.Public))
            if(x.Name=="GetService"&&x.IsGenericMethodDefinition&&x.GetParameters().Length==1&&x.GetParameters()[0].ParameterType.IsAssignableFrom(target.GetType())) try{return x.MakeGenericMethod(service).Invoke(null,new[]{target});}catch{} }
            return null;
        }
        static Delegate NoOp(Type type)
        {
            var invoke=type.GetMethod("Invoke"); var p=invoke.GetParameters().Select(x=>Expression.Parameter(x.ParameterType,x.Name)).ToArray();
            return Expression.Lambda(type,Expression.Empty(),p).Compile();
        }
        static object Prop(object o,string n){ if(o==null)return null; try{return o.GetType().GetProperty(n,BindingFlags.Instance|BindingFlags.Public)?.GetValue(o,null);}catch{return null;} }
        static object Call(object o,string n,params object[] a)
        {
            if(o==null)return null; foreach(var m in o.GetType().GetMethods(BindingFlags.Instance|BindingFlags.Public).Where(x=>x.Name==n&&x.GetParameters().Length==a.Length))
            try{return m.Invoke(o,a);}catch(ArgumentException){} catch(TargetParameterCountException){}
            throw new MissingMethodException(o.GetType().FullName,n);
        }
        static void TryCall(object o,string n){ if(o==null)return; try{o.GetType().GetMethod(n,Type.EmptyTypes)?.Invoke(o,null);}catch{} }
        static void Close(object o){ TryCall(o,"Close"); }
        static IEnumerable<object> Items(object o){ if(o is IEnumerable e) foreach(var x in e) if(x!=null) yield return x; }
    }
}
