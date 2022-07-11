import Vue from "vue";
import Account from "vue-material-design-icons/Account.vue";
import AccountCog from "vue-material-design-icons/AccountCog.vue";
import AccountEdit from "vue-material-design-icons/AccountEdit.vue";
import AccountEye from "vue-material-design-icons/AccountEye.vue";
import AccountGroup from "vue-material-design-icons/AccountGroup.vue";
import AccountStar from "vue-material-design-icons/AccountStar.vue";
import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
import ChartLine from "vue-material-design-icons/ChartLine.vue";
import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
import ClipboardArrowDownOutline from "vue-material-design-icons/ClipboardArrowDownOutline.vue";
import ClipboardArrowLeftOutline from "vue-material-design-icons/ClipboardArrowLeftOutline.vue";
import ClipboardArrowRightOutline from "vue-material-design-icons/ClipboardArrowRightOutline.vue";
import ClipboardArrowUpOutline from "vue-material-design-icons/ClipboardArrowUpOutline.vue";
import ClipboardOutline from "vue-material-design-icons/ClipboardOutline.vue";
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import DatabaseImportOutline from "vue-material-design-icons/DatabaseImportOutline.vue";
import Delete from "vue-material-design-icons/Delete.vue";
import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue";
import FileDownloadOutline from "vue-material-design-icons/FileDownloadOutline.vue";
import FolderOpen from "vue-material-design-icons/FolderOpen.vue";
import GraphOutline from "vue-material-design-icons/GraphOutline.vue";
import HomeOutline from "vue-material-design-icons/HomeOutline.vue";
import Minus from "vue-material-design-icons/Minus.vue";
import Pencil from "vue-material-design-icons/Pencil.vue";
import Plus from "vue-material-design-icons/Plus.vue";

Vue.component("ParentIcon", ClipboardArrowUpOutline);
Vue.component("ChildIcon", ClipboardArrowDownOutline);
Vue.component("PredecessorIcon", ClipboardArrowLeftOutline);
Vue.component("SuccessorIcon", ClipboardArrowRightOutline);
Vue.component("ReferencesIcon", DatabaseImportOutline);

Vue.component("TimeseriesIcon", ChartLine);
Vue.component("StructuredDataIcon", GraphOutline);
Vue.component("FileIcon", FileDocumentOutline);
Vue.component("DataObjectIcon", ClipboardOutline);

Vue.component("HomeIcon", HomeOutline);
Vue.component("CollapseIcon", ChevronUp);
Vue.component("ExtendIcon", ChevronDown);

Vue.component("DeleteIcon", Delete);
Vue.component("EditIcon", Pencil);
Vue.component("CreateIcon", Plus);
Vue.component("RemoveIcon", Minus);
Vue.component("OpenIcon", FolderOpen);
Vue.component("PermissionsIcon", AccountCog);
Vue.component("DownloadIcon", FileDownloadOutline);
Vue.component("CopyIcon", ContentCopy);
Vue.component("EyeIcon", EyeOutline);

Vue.component("UserIcon", Account);
Vue.component("UserGroupIcon", AccountGroup);
Vue.component("ReaderIcon", AccountEye);
Vue.component("WriterIcon", AccountEdit);
Vue.component("ManagerIcon", AccountStar);

Vue.component("HealthyIcon", CheckCircle);
Vue.component("UnhealthyIcon", AlertCircle);
