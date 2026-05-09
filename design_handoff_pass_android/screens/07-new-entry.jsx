function ScreenNewEntry() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{display:"flex", flexDirection:"column", height:"100%"}}>
        <div style={{
          padding:"12px 18px 10px",
          borderBottom:`1px solid ${T.border}`,
          display:"flex", alignItems:"center", gap:10,
          flexShrink:0,
        }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 11L5 7L9 3" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
          <div style={{color:T.accent, fontSize:14, fontWeight:700}}>new entry</div>
        </div>

        <div style={{flex:1, overflowY:"auto", padding:"16px 18px"}}>
          {/* path builder */}
          <Label>store path</Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, padding:"10px 12px", marginBottom:4,
            display:"flex", alignItems:"center", gap:4,
          }}>
            <span style={{
              background:T.accentDim, border:`1px solid ${T.accentMid}`,
              borderRadius:3, padding:"2px 7px",
              color:T.accent, fontSize:10,
            }}>email</span>
            <span style={{color:T.textDim, fontSize:11}}>/</span>
            <span style={{color:T.text, fontSize:11}}>protonmail</span>
            <span style={{width:6,height:13,background:T.accent,display:"inline-block",animation:"blink 1s step-end infinite", opacity:0.8}}/>
          </div>
          {/* folder quick-select */}
          <div style={{display:"flex", gap:5, marginBottom:16}}>
            {storeData.map(({folder})=>(
              <div key={folder} style={{
                padding:"3px 8px", borderRadius:3, fontSize:9,
                background: folder==="email"?T.accentDim:T.surface,
                border:`1px solid ${folder==="email"?T.accent:T.border2}`,
                color: folder==="email"?T.accent:T.textDim,
                cursor:"pointer",
              }}>{folder}</div>
            ))}
          </div>

          {/* password */}
          <Label>password</Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.accent}44`,
            borderRadius:T.r, padding:"10px 12px", marginBottom:6,
            display:"flex", alignItems:"center", gap:6,
          }}>
            <span style={{color:T.text, fontSize:11, flex:1, letterSpacing:"0.05em"}}>••••••••••••••••</span>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <path d="M6.5 2C3.5 2 1 6.5 1 6.5s2.5 4.5 5.5 4.5 5.5-4.5 5.5-4.5S9.5 2 6.5 2z" stroke={T.textDim} strokeWidth="1.1"/>
              <circle cx="6.5" cy="6.5" r="1.5" fill={T.textDim}/>
            </svg>
          </div>
          <div style={{
            display:"flex", alignItems:"center", gap:8,
            padding:"7px 0 14px",
            borderBottom:`1px solid ${T.border}`,
            marginBottom:14,
            cursor:"pointer",
          }}>
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
              <path d="M6 1v2M6 9v2M1 6h2M9 6h2M2.9 2.9l1.4 1.4M7.7 7.7l1.4 1.4M2.9 9.1l1.4-1.4M7.7 4.3l1.4-1.4" stroke={T.accent} strokeWidth="1.2" strokeLinecap="round"/>
            </svg>
            <span style={{color:T.accent, fontSize:10}}>generate password instead</span>
          </div>

          {/* notes */}
          <Label>notes <span style={{color:T.textFaint, fontWeight:400}}>optional</span></Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, padding:"10px 12px", marginBottom:20,
            minHeight:64, color:T.textFaint, fontSize:11,
          }}>username, recovery codes…</div>

          <Btn full>$ pass insert email/protonmail</Btn>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenNewEntry = ScreenNewEntry;
