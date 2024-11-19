import argparse
import difflib
import inspect
import sys
from typing import Any, Dict, List, Tuple

from shepard_client import *
from shepard_client_local import *


def diff_string(string_a: str, string_b: str):
    green = "\x1b[38;5;16;48;5;2m"
    red = "\x1b[38;5;16;48;5;1m"
    end = "\x1b[0m"
    output = []
    matcher = difflib.SequenceMatcher(None, string_a, string_b)
    for opcode, a0, a1, b0, b1 in matcher.get_opcodes():
        if opcode == "equal":
            output += [string_a[a0:a1]]
        elif opcode == "insert":
            output += [green + string_b[b0:b1] + end]
        elif opcode == "delete":
            output += [red + string_a[a0:a1] + end]
        elif opcode == "replace":
            output += [green + string_b[b0:b1] + end]
            output += [red + string_a[a0:a1] + end]
    output = "".join(output)
    return output


def compare_classes(
    class1: Any,
    class2: Any,
    is_diffing_signatures: bool = False,
    is_using_colors: bool = False,
) -> Tuple[List[str], List[str]]:
    differences_orig_to_new: List[str] = []
    differences_new_to_orig: List[str] = []

    # Compare class attributes (excluding methods)
    class1_attrs = set(
        attr
        for attr in dir(class1)
        if not callable(getattr(class1, attr)) and not attr.startswith("__")
    )
    class2_attrs = set(
        attr
        for attr in dir(class2)
        if not callable(getattr(class2, attr)) and not attr.startswith("__")
    )

    missing_in_class2 = class1_attrs - class2_attrs
    missing_in_class1 = class2_attrs - class1_attrs

    if missing_in_class2:
        differences_orig_to_new.append(
            f"Attributes in original {class1.__name__} but not in new {class2.__name__}: {missing_in_class2}"
        )
    if missing_in_class1:
        differences_new_to_orig.append(
            f"Attributes in new {class2.__name__} but not in original {class1.__name__}: {missing_in_class1}"
        )

    # Compare method names
    class1_methods = set(
        attr
        for attr in dir(class1)
        if callable(getattr(class1, attr)) and not attr.startswith("__")
    )
    class2_methods = set(
        attr
        for attr in dir(class2)
        if callable(getattr(class2, attr)) and not attr.startswith("__")
    )

    missing_methods_in_class2 = class1_methods - class2_methods
    missing_methods_in_class1 = class2_methods - class1_methods

    if missing_methods_in_class2:
        differences_orig_to_new.append(
            f"Methods in original {class1.__name__} but not in new {class2.__name__}: {missing_methods_in_class2}"
        )
    if missing_methods_in_class1:
        differences_new_to_orig.append(
            f"Methods in new {class2.__name__} but not in original {class1.__name__}: {missing_methods_in_class1}"
        )

    # Optionally compare method signatures
    # signature differences are extremely diverse and analyzing them to be a breaking change is too complex for now
    # so we just want to print out the information that there are differences in signature
    if is_diffing_signatures:
        common_methods = class1_methods & class2_methods
        for method in common_methods:
            try:
                method1_sig = inspect.signature(getattr(class1, method))
                method2_sig = inspect.signature(getattr(class2, method))
                method1_sig_str = str(method1_sig)
                # the second class (local package), was renamed to 'shepard_client_local' to avoid naming conflicts
                # we have to manually revert these name changes
                method2_sig_str = str(method2_sig).replace(
                    "shepard_client_local.", "shepard_client."
                )
                if method1_sig_str != method2_sig_str:
                    if is_using_colors:
                        differences_new_to_orig.append(
                            f"Method '{method}' signature differs:\n{diff_string(method1_sig_str, method2_sig_str)}"
                        )
                    else:
                        differences_new_to_orig.append(
                            f"Method '{method}' signature differs:\n->Original package: {method1_sig_str}\n->New package: {method2_sig_str}"
                        )
            except ValueError as e:
                # prevent comparing builtin methods
                if str(e).startswith("no signature found for builtin"):
                    continue
                else:
                    raise

    return (differences_orig_to_new, differences_new_to_orig)


def find_same_class_names(
    orig_class_name_list: List[str], new_class_name_list: List[str]
) -> Tuple[List[str], List[str], List[str]]:
    """
    From two lists of class names, create one list of same class names, which are then further compared, and a list of different class names, that probably changed

    Expects two lists of class names as input parameters.
    The first input list is meant to be the original/ "truth" list of class names, which is compared to the second list of class names.
    ---
    Returns: Tuple[List[str], List[str]] - same_class_names_list, different_class_names_list
    """
    same_class_names: List[str] = set()
    classes_not_in_orig_class: List[str] = set()
    classes_not_in_new_class: List[str] = set()

    same_class_names = list(set(orig_class_name_list) & set(new_class_name_list))
    classes_not_in_orig_class = list(
        set(orig_class_name_list) - set(new_class_name_list)
    )
    classes_not_in_new_class = list(
        set(new_class_name_list) - set(orig_class_name_list)
    )

    same_class_names.sort()
    classes_not_in_orig_class.sort()
    classes_not_in_new_class.sort()

    return (
        same_class_names,
        classes_not_in_orig_class,
        classes_not_in_new_class,
    )


def handle_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="Python Client Diff Tool",
    )
    parser.add_argument(
        "-s",
        "--signature",
        help="Compare function signatures and print colorful diff. Changing function signatures are not checked for breaking changes. Only has an effect when '-p' is set.",
        action="store_true",
    )
    parser.add_argument(
        "-p",
        "--print-all",
        help="Print all differences, not just breaking differences",
        action="store_true",
    )
    parser.add_argument(
        "-c",
        "--color",
        help="Use colored output for signature diffs",
        action="store_true",
    )
    return parser.parse_args()


def main():
    args = handle_args()
    is_diffing_signatures: bool = args.signature
    is_printing_all: bool = args.print_all
    is_using_colors: bool = args.color

    # get all exported classes from both shepard clients
    # use their class names as dict keys and their class objects as values
    remote_package_classes_dict: Dict[str, Any] = {}
    for cls_name, cls_obj in inspect.getmembers(sys.modules["shepard_client"]):
        # don't include private functions
        if not cls_name.startswith("__"):
            remote_package_classes_dict[cls_name] = cls_obj

    local_package_classes_dict: Dict[str, Any] = {}
    for cls_name, cls_obj in inspect.getmembers(sys.modules["shepard_client_local"]):
        if not cls_name.startswith("__"):
            local_package_classes_dict[cls_name] = cls_obj

    remote_package_classes: List[str] = list(remote_package_classes_dict.keys())
    local_package_classes: List[str] = list(local_package_classes_dict.keys())

    (same_class_name_list, classes_not_in_orig_class, classes_not_in_new_class) = (
        find_same_class_names(remote_package_classes, local_package_classes)
    )

    if is_printing_all:
        print(
            f"Classes with the same name from both packages: {same_class_name_list}\n"
        )
        print(
            f"Classes that are in the new package but not in the old package: {classes_not_in_new_class}\n"
        )

    diff_list_orig_to_new: List[str] = []
    diff_list_new_to_orig: List[str] = []
    for class_name in same_class_name_list:
        (diffs_orig_to_new, diffs_new_to_orig) = compare_classes(
            remote_package_classes_dict[class_name],
            local_package_classes_dict[class_name],
            is_diffing_signatures=is_diffing_signatures,
            is_using_colors=is_using_colors,
        )
        diff_list_orig_to_new.append(
            "\n".join(
                diffs_orig_to_new,
            )
        )
        diff_list_new_to_orig.append("\n".join(diffs_new_to_orig))

    if is_printing_all:
        if len([x for x in diff_list_new_to_orig if x]) > 0:
            print("\nDifference from new package to original package:")
            print("\n".join([x for x in diff_list_new_to_orig if x]))

    # missing classes from the old package in the new package are a breaking change
    if len([x for x in classes_not_in_orig_class if x]) > 0:
        print(
            f"Classes that are in the old package but not in the new package: {classes_not_in_orig_class}\n"
        )
        sys.exit(
            "WARNING: Classes from the original client package are missing in the new client package!"
        )

    # missing functions, attributes from the old package in the new package are a breaking change
    if len([x for x in diff_list_orig_to_new if x]) > 0:
        print(
            "Differences (possible breaking changes) from original package to new package:"
        )
        print("\n".join([x for x in diff_list_orig_to_new if x]))
        sys.exit(
            "WARNING: Class attributes or methods from the original client package are missing in the new client package!"
        )


if __name__ == "__main__":
    main()
