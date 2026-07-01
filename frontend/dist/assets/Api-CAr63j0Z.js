import{c as s,u as l,j as e,e as c}from"./index-Cav3SvLq.js";import{G as y}from"./globe-EKvXqLEc.js";/**
 * @license lucide-react v0.344.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const p=s("ArrowLeft",[["path",{d:"m12 19-7-7 7-7",key:"1l729n"}],["path",{d:"M19 12H5",key:"x3x0zl"}]]);/**
 * @license lucide-react v0.344.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const h=s("Mic",[["path",{d:"M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z",key:"131961"}],["path",{d:"M19 10v2a7 7 0 0 1-14 0v-2",key:"1vc78b"}],["line",{x1:"12",x2:"12",y1:"19",y2:"22",key:"x3vr5v"}]]);/**
 * @license lucide-react v0.344.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const u=s("Paperclip",[["path",{d:"m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48",key:"1u3ebp"}]]);/**
 * @license lucide-react v0.344.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const m=s("Type",[["polyline",{points:"4 7 4 4 20 4 20 7",key:"1nosan"}],["line",{x1:"9",x2:"15",y1:"20",y2:"20",key:"swin9y"}],["line",{x1:"12",x2:"12",y1:"4",y2:"20",key:"1tx1rr"}]]);/**
 * @license lucide-react v0.344.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const k=s("Volume2",[["polygon",{points:"11 5 6 9 2 9 2 15 6 15 11 19 11 5",key:"16drj5"}],["path",{d:"M15.54 8.46a5 5 0 0 1 0 7.07",key:"ltjumu"}],["path",{d:"M19.07 4.93a10 10 0 0 1 0 14.14",key:"1kegas"}]]);function v({title:a,showBack:t=!0,showGlobe:n=!0,onBack:r,rightElement:o}){const i=l();return e.jsxs("div",{className:"screen-header bg-white sticky top-0 z-10",children:[e.jsx("div",{className:"w-8",children:t&&e.jsx("button",{onClick:r??(()=>i(-1)),className:"p-1 rounded-full hover:bg-gray-100 transition-colors",children:e.jsx(p,{className:"w-5 h-5 text-gray-700"})})}),e.jsx("span",{className:"text-base font-semibold text-gray-900",children:a}),e.jsx("div",{className:"w-8 flex justify-end",children:o??(n&&e.jsx("button",{className:"p-1 rounded-full hover:bg-gray-100 transition-colors",children:e.jsx(y,{className:"w-5 h-5 text-gray-500"})}))})]})}const g={sendMessage:(a,t)=>c.post("/assistant/chat",{message:a,history:t})};export{h as M,u as P,v as S,m as T,k as V,g as a};
