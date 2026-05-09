function ScreenHomeTreeV2() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{display:"flex", flexDirection:"column", height:"100%"}}>
        <div style={{padding:"12px 18px 10px", borderBottom:`1px solid ${T.border}`, display:"flex", alignItems:"center", justifyContent:"space-between", flexShrink:0}}>
          <div>
            <div style={{color:T.textDim, fontSize:9, letterSpacing:"0.1em"}}>~/.password-store</div>
            <div style={{color:T.accent, fontSize:14, fontWeight:700, marginTop:1}}>pass<span style={{color:T.textDim,fontWeight:300}}>.android</span></div>
          </div>
          <div style={{display:"flex", gap:10, alignItems:"center"}}>
            <div style={{display:"flex", alignItems:"center", gap:5, background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, padding:"5px 9px"}}>
              <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                <path d="M9.5 5.5A4 4 0 1 1 7 2" stroke={T.textDim} strokeWidth="1.2" strokeLinecap="round"/>
                <path d="M7 2l1 2.5-2.5.5" stroke={T.textDim} strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span style={{color:T.textDim, fontSize:9}}>sync</span>
            </div>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <rect x="0.5" y="0.5" width="5" height="5" rx="1" stroke={T.accent} strokeWidth="1.1"/>
              <rect x="7.5" y="0.5" width="5" height="5" rx="1" stroke={T.textDim} strokeWidth="1.1"/>
              <rect x="0.5" y="7.5" width="5" height="5" rx="1" stroke={T.textDim} strokeWidth="1.1"/>
              <rect x="7.5" y="7.5" width="5" height="5" rx="1" stroke={T.textDim} strokeWidth="1.1"/>
            </svg>
          </div>
        </div>
        <div style={{padding:"8px 18px", borderBottom:`1px solid ${T.border}`, flexShrink:0}}>
          <div style={{background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, padding:"7px 12px", display:"flex", alignItems:"center", gap:8}}>
            <span style={{color:T.textDim, fontSize:11}}>$</span>
            <span style={{color:T.textDim, fontSize:11}}>grep -r ""</span>
            <span style={{width:6,height:12,background:T.accent,display:"inline-block",animation:"blink 1s step-end infinite",opacity:0.8}}/>
          </div>
        </div>
        <div style={{flex:1, overflowY:"auto", padding:"6px 0"}}>
          {storeData.map(({folder, entries}) => (
            <div key={folder}>
              <div style={{display:"flex", alignItems:"center", gap:8, padding:"7px 18px"}}>
                <span style={{color:T.accentMid, fontSize:10}}>▼</span>
                <span style={{color:T.accent, fontSize:11, opacity:0.75}}>{folder}/</span>
                <span style={{color:T.textFaint, fontSize:9, marginLeft:"auto"}}>{entries.length}</span>
              </div>
              {entries.map((e,i) => (
                <div key={e} style={{display:"flex", alignItems:"center", padding:"8px 18px 8px 40px", borderBottom:`1px solid ${T.border}`, background:e==="gmail"?T.accentDim:"transparent"}}>
                  <span style={{color:T.textFaint, fontSize:10, marginRight:8}}>{i<entries.length-1?"├─":"└─"}</span>
                  <span style={{color:e==="gmail"?T.accent:T.text, fontSize:11, flex:1}}>{e}</span>
                  <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M3.5 8L6.5 5L3.5 2" stroke={T.textFaint} strokeWidth="1.2" strokeLinecap="round"/></svg>
                </div>
              ))}
            </div>
          ))}
        </div>
        <FAB/>
      </div>
    </Phone>
  );
}
window.ScreenHomeTreeV2 = ScreenHomeTreeV2;
