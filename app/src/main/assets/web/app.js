const $=id=>document.getElementById(id);
let currentFolder="";
let terminalHistory=["PhoneVPS terminal initialized.","Type help to see supported commands."];
function bridge(){return typeof window.PhoneVPS!=="undefined";}
function setServerState(status){
 const online=status==="Online";
 $("hostState").textContent=status;
 $("hostState").className=online?"green":status.startsWith("Error")?"red":"";
 $("hostDetail").textContent=online?"Serving /projects on port 8080":status.startsWith("Error")?"Startup failed":"Not hosting";
 $("topStatus").innerHTML="<i></i> "+(online?"ONLINE":status.toUpperCase());
 $("serverMessage").textContent=online?"Server is ONLINE · Local: http://127.0.0.1:8080 · LAN: http://"+(PhoneVPS.lanAddress?PhoneVPS.lanAddress():"your-phone-ip")+":8080":status.startsWith("Error")?status:"Server is offline.";
}
function refresh(){
 try{
  if(!bridge()){$("pythonState").textContent="Unavailable";$("pythonDetail").textContent="Native app bridge missing";setServerState("Error: app bridge missing");return;}
  const ok=PhoneVPS.pythonReady();
  $("pythonState").textContent=ok?"Ready":"Error";
  $("pythonDetail").textContent=ok?"Embedded Python 3.11":"Python runtime failed — check app installation";
  setServerState(PhoneVPS.hostingStatus());
 }catch(e){$("pythonState").textContent="Error";$("pythonDetail").textContent=String(e);setServerState("Error: "+String(e));}
}
function showTab(tab){
 document.querySelectorAll(".view").forEach(x=>x.hidden=x.id!==tab);
 document.querySelectorAll(".nav button").forEach(x=>x.classList.toggle("active",x.dataset.tab===tab));
 if(tab==="files")loadFiles();
}
document.querySelectorAll(".nav button").forEach(b=>b.onclick=()=>showTab(b.dataset.tab));
$("start").onclick=()=>{const result=PhoneVPS.startHosting();$("serverMessage").textContent=result;setTimeout(refresh,350);setTimeout(refresh,1200);};
$("stop").onclick=()=>{const result=PhoneVPS.stopHosting();$("serverMessage").textContent=result;setTimeout(refresh,500);};
$("openServer").onclick=()=>{if(PhoneVPS.hostingStatus()==="Online")window.open("http://127.0.0.1:8080","_blank");else $("serverMessage").textContent="Start the server first.";};
$("refresh").onclick=refresh;
$("terminalRun").onclick=runTerminal;
$("terminalInput").addEventListener("keydown",e=>{if(e.key==="Enter"){e.preventDefault();runTerminal();}});
function renderTerminal(){ $("terminalOutput").textContent=terminalHistory.slice(-150).join("\n"); $("terminalOutput").scrollTop=$("terminalOutput").scrollHeight; }
function runTerminal(){
 const command=$("terminalInput").value.trim();if(!command)return;
 $("terminalInput").value="";
 if(!bridge()){terminalHistory.push("$ "+command,"ERROR: Native PhoneVPS bridge is unavailable.");renderTerminal();return;}
 terminalHistory.push("$ "+command);
 try{
  const result=PhoneVPS.runCommand(command);
  if(result==="__CLEAR__")terminalHistory=[];
  else terminalHistory.push(result===null||result===undefined?"ERROR: PhoneVPS returned no result.":String(result));
 }catch(e){terminalHistory.push("TERMINAL ERROR: "+(e&&e.stack?e.stack:String(e)));}
 renderTerminal();
}
function loadFiles(){
 if(!bridge())return;
 let data=[];
 try{data=JSON.parse(PhoneVPS.listFiles(currentFolder)||"[]");}catch(e){$("fileMessage").textContent="Could not load files: "+e;return;}
 $("pathLabel").textContent="/projects/"+currentFolder;
 const list=$("fileList");list.innerHTML="";
 if(currentFolder){const up=document.createElement("button");up.className="file";up.textContent="⬅ Parent folder";up.onclick=()=>{currentFolder=currentFolder.includes("/")?currentFolder.slice(0,currentFolder.lastIndexOf("/")):"";loadFiles();};list.appendChild(up);}
 if(!data.length){const empty=document.createElement("p");empty.className="muted";empty.textContent="No files here yet. Tap Create file to add your first project.";list.appendChild(empty);}
 data.forEach(item=>{
  const path=currentFolder?currentFolder+"/"+item.name:item.name;
  const b=document.createElement("button");b.className="file";
  const label=document.createElement("span");label.textContent=(item.directory?"📁 ":"📄 ")+item.name;
  const meta=document.createElement("small");meta.textContent=item.directory?"Folder":item.size+" bytes";
  b.append(label,meta);b.onclick=()=>{if(item.directory){currentFolder=path;loadFiles();}else openFile(path);};list.appendChild(b);
 });
}
function openFile(path){
 $("fileName").value=path;
 $("fileContent").value=PhoneVPS.readFile(path);
 $("fileEditor").hidden=false;
 $("fileMessage").textContent="Editing "+path;
}
$("newFile").onclick=()=>{$("fileEditor").hidden=false;$("fileName").value=currentFolder?(currentFolder+"/main.py"):"main.py";$("fileContent").value="";$("fileMessage").textContent="Enter a filename and write your code, then save.";};
$("newFolder").onclick=()=>{const name=prompt("Enter folder name");if(!name)return;const path=currentFolder?currentFolder+"/"+name:name;$("fileMessage").textContent=PhoneVPS.createFolder(path);loadFiles();};
$("reloadFiles").onclick=loadFiles;
$("saveFile").onclick=()=>{const name=$("fileName").value.trim();if(!name){$("fileMessage").textContent="Enter a file name first.";return;}const result=PhoneVPS.saveFile(name,$("fileContent").value);$("fileMessage").textContent=result;loadFiles();};
$("runFile").onclick=()=>{const name=$("fileName").value.trim();if(!name){$("fileMessage").textContent="Save a Python file first.";return;}try{const result=PhoneVPS.runCommand("python "+JSON.stringify(name));terminalHistory.push("$ python "+name,String(result));renderTerminal();$("fileMessage").textContent="Finished. See Terminal tab for output.";}catch(e){terminalHistory.push("RUN ERROR: "+String(e));renderTerminal();$("fileMessage").textContent="Run failed: "+String(e);}};
$("deleteFile").onclick=()=>{const path=$("fileName").value;if(!path||!confirm("Delete "+path+"?"))return;$("fileMessage").textContent=PhoneVPS.deletePath(path);$("fileEditor").hidden=true;loadFiles();};
refresh();
setInterval(()=>{if(bridge())setServerState(PhoneVPS.hostingStatus());},2000);
