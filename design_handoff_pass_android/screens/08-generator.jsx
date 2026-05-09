function ScreenGenerator() {
  const T = React.useContext(ThemeContext);
  const charsets = [
    {key:"upper", label:"A–Z", active:true},
    {key:"lower", label:"a–z", active:true},
    {key:"nums",  label:"0–9", active:true},
    {key:"syms",  label:"!@#…", active:false},
  ];
  const generated = "kR7mN2pXvL9q";
  const len = 16;
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
          <div style={{color:T.accent, fontSize:14, fontWeight:700}}>generate</div>
        </div>

        <div style={{flex:1, overflowY:"auto", padding:"20px 18px"}}>
          {/* result */}
          <div style={{
            background:T.surface, border:`1px solid ${T.accent}55`,
            borderRadius:T.r, padding:"16px 14px", marginBottom:6,
            textAlign:"center",
            boxShadow:`0 0 24px ${T.accentDim}`,
          }}>
            <div style={{
              color:T.accent, fontSize:16, fontWeight:600,
              letterSpacing:"0.12em", marginBottom:12,
              fontFamily:T.mono,
            }}>{generated}</div>
            <div style={{display:"flex", gap:6}}>
              <Btn style={{flex:1, fontSize:9}}>copy</Btn>
              <Btn dim style={{flex:1, fontSize:9}}>↺ regenerate</Btn>
            </div>
          </div>

          {/* entropy */}
          <div style={{
            display:"flex", gap:4, alignItems:"center",
            padding:"6px 0 18px",
          }}>
            {[1,2,3,4].map(i=>(
              <div key={i} style={{
                flex:1, height:3, borderRadius:4,
                background: i<=3 ? T.accent : T.border2,
                opacity: i===3?1:i===2?0.6:0.4,
              }}/>
            ))}
            <span style={{color:T.textDim, fontSize:9, marginLeft:6}}>good (71 bits)</span>
          </div>

          {/* length */}
          <Label>length — {len}</Label>
          <div style={{marginBottom:18, paddingInline:2}}>
            {/* track */}
            <div style={{height:3, background:T.border2, borderRadius:4, position:"relative", margin:"10px 0"}}>
              <div style={{height:"100%", width:"60%", background:T.accent, borderRadius:4}}/>
              <div style={{
                width:14,height:14, borderRadius:"50%",
                background:T.accent, border:`2px solid ${T.bg}`,
                position:"absolute", top:-5.5, left:"calc(60% - 7px)",
                boxShadow:`0 0 8px ${T.accent}`,
              }}/>
            </div>
            <div style={{display:"flex", justifyContent:"space-between", color:T.textFaint, fontSize:9}}>
              <span>8</span><span>16</span><span>32</span><span>64</span>
            </div>
          </div>

          {/* charsets */}
          <Label>character sets</Label>
          <div style={{display:"flex", gap:6, marginBottom:20}}>
            {charsets.map(c=>(
              <div key={c.key} style={{
                flex:1, padding:"8px 4px", borderRadius:T.r,
                background: c.active ? T.accentDim : T.surface,
                border:`1px solid ${c.active ? T.accent : T.border2}`,
                color: c.active ? T.accent : T.textDim,
                fontSize:10, textAlign:"center", cursor:"pointer",
              }}>{c.label}</div>
            ))}
          </div>

          {/* use it */}
          <Btn full>use this password</Btn>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenGenerator = ScreenGenerator;
