const $=id=>document.getElementById(id);
let currentFolder="";
let terminalHistory=[];
function bridge(){return typeof window.PhoneVPS!=="undefined";}
function refresh(){
 if(!bridge()){$("pythonState").textContent="Unavailable";$("pythonDetail").textContent="App bridge missing";return;}
 const ok=PhoneVPS.pythonReady();
 $("pythonState").textContent=ok?"Ready":"Error";
 $("pythonDetail").textContent=ok?"Embedded Python 3.11":"Could not initialize Python";
 $("hostState").textContent=PhoneVPS.hostingStatus();
}
function showTab(tab){
 document.querySelectorAll(".view").forEach(x=>x.hidden=x.id!==tab);
 document.querySelectorAll(".nav button").forEach(x=>x.classList.toggle("active",x.dataset.tab===tab));
 if(tab==="files") loadFiles();
}
document.querySelectorAll(".nav button").forEach(b=>b.onclick=()=>showTab(b.dataset.tab));
$("start").onclick=()=>{const result=PhoneVPS.startHosting();$("output").textContent=result;setTimeout(refresh,700);};
$("stop").onclick=()=>{const result=PhoneVPS.stopHosting();$("output").textContent=result;setTimeout(refresh,700);};
$("openServer").onclick=()=>window.open("http://127.0.0.1:8080","_blank");
$("refresh").onclick=refresh;
$("run").onclick=()=>{$("output").textContent=PhoneVPS.runPython($("code").value);};
$("terminalRun").onclick=()=>runTerminal();
$("terminalInput").addEventListener("keydown",e=>{if(e.key==="Enter"){e.preventDefault();runTerminal();}});
function runTerminal(){
 const command=$("terminalInput").value.trim();if(!command)return;
 terminalHistory.push("$ "+command);
 const result=PhoneVPS.runPython(command);
 terminalHistory.push(result);
 $("terminalOutput").textContent=terminalHistory.slice(-80).join("\n");
 $("terminalInput").value="";
 $("terminalOutput").scrollTop=$("terminalOutput").scrollHeight;
}
function loadFiles(){
 if(!bridge())return;
 const data=JSON.parse(PhoneVPS.listFiles(currentFolder)||"[]");
 $("pathLabel").textContent="/projects/"+currentFolder;
 const list=$("fileList");list.innerHTML="";
 if(currentFolder){const up=document.createElement("button");up.className="file";up.textContent="⬅ ..";up.onclick=()=>{currentFolder=currentFolder.includes("/")?currentFolder.slice(0,currentFolder.lastIndexOf("/")):"";loadFiles();};list.appendChild(up);}
 data.forEach(item=>{
  const path=currentFolder?currentFolder+"/"+item.name:item.name;
  const b=document.createElement("button");b.className="file";
  const label=document.createElement("span");label.textContent=(item.directory?"📁 ":"📄 ")+item.name;
  const meta=document.createElement("small");meta.textContent=item.directory?"Folder":(item.size+" bytes");
  b.append(label,meta);
  b.onclick=()=>{if(item.directory){currentFolder=path;loadFiles();}else openFile(path);};
  list.appendChild(b);
 });
}
function openFile(path){
 const content=PhoneVPS.readFile(path);
 $("fileName").value=path;
 $("fileContent").value=content;
 $("fileEditor").hidden=false;
}
$("saveFile").onclick=()=>{const result=PhoneVPS.saveFile($("fileName").value,$("fileContent").value);$("fileMessage").textContent=result;loadFiles();};
$("newFile").onclick=()=>{$("fileEditor").hidden=false;$("fileName").value=currentFolder?(currentFolder+"/new.py"):"new.py";$("fileContent").value="";};
$("newFolder").onclick=()=>{const name=prompt("Folder name");if(!name)return;const path=currentFolder?currentFolder+"/"+name:name;$("fileMessage").textContent=PhoneVPS.createFolder(path);loadFiles();};
$("deleteFile").onclick=()=>{const path=$("fileName").value;if(!path||!confirm("Delete "+path+"?"))return;$("fileMessage").textContent=PhoneVPS.deletePath(path);$("fileEditor").hidden=true;loadFiles();};
refresh();
