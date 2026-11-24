const $=id=>document.getElementById(id);
function bridge(){return typeof window.PhoneVPS!=="undefined";}
function refresh(){
 if(!bridge()){$("pythonState").textContent="Unavailable";$("pythonDetail").textContent="App bridge missing";return;}
 const ok=PhoneVPS.pythonReady();
 $("pythonState").textContent=ok?"Ready":"Error";
 $("pythonDetail").textContent=ok?"Embedded Python 3.11":"Could not initialize Python";
 $("hostState").textContent=PhoneVPS.hostingStatus();
}
$("start").onclick=()=>{const result=PhoneVPS.startHosting();$("output").textContent=result;setTimeout(refresh,700);};
$("stop").onclick=()=>{const result=PhoneVPS.stopHosting();$("output").textContent=result;setTimeout(refresh,700);};
$("openServer").onclick=()=>window.open("http://127.0.0.1:8080","_blank");
$("refresh").onclick=refresh;
$("run").onclick=()=>{$("output").textContent=PhoneVPS.runPython($("code").value);};
refresh();
