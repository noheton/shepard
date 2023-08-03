import BootstrapVue, {
  BIconArrowDownSquare,
  BIconArrowLeftSquare,
  BIconArrowRightSquare,
  BIconArrowUpSquare,
  BIconBoxArrowInDownRight,
  BIconCheckCircle,
  BIconCheckSquare,
  BIconChevronDown,
  BIconChevronUp,
  BIconDash,
  BIconDiagram3,
  BIconDownload,
  BIconEye,
  BIconFiles,
  BIconFolder2Open,
  BIconGraphUp,
  BIconKeyFill,
  BIconLockFill,
  BIconPencil,
  BIconPerson,
  BIconPersonLinesFill,
  BIconPersonPlus,
  BIconPlus,
  BIconSquare,
  BIconTags,
  BIconTrash,
  BIconUnlockFill,
  BIconXCircle,
  BIconXLg,
} from "bootstrap-vue";
import Vue from "vue";
import "../assets/custom.scss";

Vue.use(BootstrapVue);

Vue.component("ParentIcon", BIconArrowUpSquare);
Vue.component("ChildIcon", BIconArrowDownSquare);
Vue.component("PredecessorIcon", BIconArrowLeftSquare);
Vue.component("SuccessorIcon", BIconArrowRightSquare);
Vue.component("ReferencesIcon", BIconBoxArrowInDownRight);

Vue.component("CollapseIcon", BIconChevronUp);
Vue.component("ExtendIcon", BIconChevronDown);

Vue.component("DeleteIcon", BIconTrash);
Vue.component("EditIcon", BIconPencil);
Vue.component("SemanticIcon", BIconTags);
Vue.component("CreateIcon", BIconPlus);
Vue.component("RemoveIcon", BIconDash);
Vue.component("OpenIcon", BIconFolder2Open);
Vue.component("PermissionsIcon", BIconPersonLinesFill);
Vue.component("DownloadIcon", BIconDownload);
Vue.component("CopyIcon", BIconFiles);
Vue.component("EyeIcon", BIconEye);
Vue.component("XIcon", BIconXLg);

Vue.component("UserIcon", BIconPerson);
Vue.component("UserGroupIcon", BIconPersonPlus);
Vue.component("ReaderIcon", BIconLockFill);
Vue.component("WriterIcon", BIconUnlockFill);
Vue.component("ManagerIcon", BIconKeyFill);

Vue.component("HealthyIcon", BIconCheckCircle);
Vue.component("UnhealthyIcon", BIconXCircle);

Vue.component("PlottingIcon", BIconGraphUp);
Vue.component("GraphIcon", BIconDiagram3);

Vue.component("CheckboxChecked", BIconCheckSquare);
Vue.component("CheckboxEmpty", BIconSquare);
