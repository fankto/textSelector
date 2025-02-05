#!/bin/bash
# extract_code.sh
#
# This script searches for files with specified extensions in two sets of directories:
#   1. The directory where the script is located (the "root").
#   2. Additional folders (relative to the script's location) that you specify.
#
# It excludes any directories whose names match any of the patterns provided in the
# exclude_directories list.
#
# For every matching file, the script appends to the output file:
#   - A comment line with the file’s relative path (relative to the script's location),
#   - The file’s content,
#   - Three newline characters.
#
# Customize the arrays below as needed.

# 1. Determine the directory where this script resides.
base_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# 2. Array of additional folders (relative to base_dir) to search.
additional_directories=("java" "res")
# Example: additional_directories=("src" "lib")

# 3. Array of file extensions (without the dot) to include.
extensions=("kt" "xml")
# Modify as needed, e.g., extensions=("py" "cpp" "java")

# 4. Array of directory name patterns to exclude.
#    Folders whose names contain any of these strings will be skipped.
exclude_directories=("mipmap" "xml")

# 5. Name of the output file.
output_file="extracted_code.txt"

# Clear (or create) the output file.
> "$output_file"

# 6. Build an array of directories to search:
#    Always include the base (script’s) directory,
#    and any additional directories (if they exist).
directories_to_search=("$base_dir")
for dir in "${additional_directories[@]}"; do
    full_path="$base_dir/$dir"
    if [ -d "$full_path" ]; then
        directories_to_search+=("$full_path")
    else
        echo "Directory '$full_path' does not exist. Skipping..."
    fi
done

# 7. Declare an associative array to track processed files (to avoid duplicates).
declare -A seen_files

# 8. Build the find command arguments for file extensions.
#    This creates an expression like: -type f \( -iname "*.py" -o -iname "*.js" -o -iname "*.sh" \)
pattern_args=(-type f \( )
for ext in "${extensions[@]}"; do
    pattern_args+=(-iname "*.$ext" -o)
done
# Remove the trailing -o.
unset 'pattern_args[${#pattern_args[@]}-1]'
pattern_args+=(\))

# 9. Build the exclusion expression for directories (if any are specified).
exclude_expr=()
if [ ${#exclude_directories[@]} -gt 0 ]; then
    for excl in "${exclude_directories[@]}"; do
        exclude_expr+=(-iname "*${excl}*" -o)
    done
    # Remove the trailing -o.
    unset 'exclude_expr[${#exclude_expr[@]}-1]'
fi

# 10. Loop over each directory to search.
for dir in "${directories_to_search[@]}"; do
    echo "Processing directory: $dir"

    # Build the full find command.
    # If there are exclusions, add the exclusion clause.
    if [ ${#exclude_expr[@]} -gt 0 ]; then
        find_command=(find "$dir" \( -type d \( "${exclude_expr[@]}" \) -prune \) -o \( "${pattern_args[@]}" -print0 \))
    else
        find_command=(find "$dir" "${pattern_args[@]}" -print0)
    fi

    # Use process substitution so that the while loop runs in the current shell.
    while IFS= read -r -d '' file; do
        # Avoid processing the same file twice (if directories overlap).
        if [ -z "${seen_files["$file"]}" ]; then
            seen_files["$file"]=1

            # Convert the absolute file path into a relative path (relative to base_dir).
            if [[ "$file" == "$base_dir/"* ]]; then
                relative_path="${file#$base_dir/}"
            else
                relative_path="$file"
            fi

            # Write a comment with the relative file path.
            echo "# $relative_path" >> "$output_file"
            # Append the file's content.
            cat "$file" >> "$output_file"
            # Append three newlines.
            echo -e "\n\n\n" >> "$output_file"
        fi
    done < <("${find_command[@]}")
done

echo "Extraction complete. Output file: $output_file"
