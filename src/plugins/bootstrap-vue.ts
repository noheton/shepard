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
  BIconCode,
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
  BIconTextCenter,
  BIconTextLeft,
  BIconTextParagraph,
  BIconTextRight,
  BIconTrash,
  BIconTypeBold,
  BIconTypeH1,
  BIconTypeH2,
  BIconTypeH3,
  BIconTypeItalic,
  BIconTypeStrikethrough,
  BIconTypeUnderline,
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

Vue.component("TypeBold", BIconTypeBold);
Vue.component("TypeItalic", BIconTypeItalic);
Vue.component("TypeStrike", BIconTypeStrikethrough);
Vue.component("TypeUnderline", BIconTypeUnderline);
Vue.component("TypeCode", BIconCode);
Vue.component("TypeLeft", BIconTextLeft);
Vue.component("TypeRight", BIconTextRight);
Vue.component("TypeCenter", BIconTextCenter);
Vue.component("TypeH1", BIconTypeH1);
Vue.component("TypeH2", BIconTypeH2);
Vue.component("TypeH3", BIconTypeH3);
Vue.component("TypeText", BIconTextParagraph);
