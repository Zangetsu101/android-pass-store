function ScreenHomeFlatV2() {
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
              <line x1="0" y1="2" x2="13" y2="2" stroke={T.accent} strokeWidth="1.3" strokeLinecap="round"/>
              <line x1="0" y1="6.5" x2="13" y2="6.5" stroke={T.accent} strokeWidth="1.3" strokeLinecap="round"/>
              <line x1="0" y1="11" x2="13" y2="11" stroke={T.accent} strokeWidth="1.3" strokeLinecap="round"/>
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
        <div style={{flex:1, overflowY:"auto"}}>
          {flatEntries.map((e,i) => (
            <div key={e.name} style={{display:"flex", alignItems:"center", padding:"9px 18px", borderBottom:`1px solid ${T.border}`, background:i===1?T.accentDim:"transparent"}}>
              <span style={{color:T.textFaint, fontSize:9, marginRight:10, width:14}}>{String.fromCharCode(65+i)}</span>
              <div style={{flex:1}}>
                <div style={{color:i===1?T.accent:T.text, fontSize:12}}>{e.name}</div>
                <div style={{color:T.textFaint, fontSize:9, marginTop:1}}>{e.path}</div>
              </div>
              <span style={{color:T.textFaint, fontSize:9}}>{e.time}</span>
            </div>
          ))}
        </div>
        <FAB/>
      </div>
    </Phone>
  );
}
window.ScreenHomeFlatV2 = ScreenHomeFlatV2;
